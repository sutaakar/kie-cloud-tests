/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package org.kie.cloud.openshift.scenario;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.kie.cloud.api.deployment.ControllerDeployment;
import org.kie.cloud.api.deployment.Deployment;
import org.kie.cloud.api.deployment.DockerDeployment;
import org.kie.cloud.api.deployment.KieServerDeployment;
import org.kie.cloud.api.deployment.SmartRouterDeployment;
import org.kie.cloud.api.deployment.WorkbenchDeployment;
import org.kie.cloud.api.deployment.constants.DeploymentConstants;
import org.kie.cloud.api.scenario.KieServerWithExternalDatabaseScenario;
import org.kie.cloud.openshift.constants.OpenShiftConstants;
import org.kie.cloud.openshift.constants.OpenShiftTemplateConstants;
import org.kie.cloud.openshift.database.driver.ExternalDriver;
import org.kie.cloud.openshift.database.external.ExternalDatabase;
import org.kie.cloud.openshift.database.external.TemplateExternalDatabaseProvider;
import org.kie.cloud.openshift.deployment.KieServerDeploymentImpl;
import org.kie.cloud.openshift.template.OpenShiftTemplate;
import org.kie.cloud.openshift.util.DockerRegistryDeployer;
import org.kie.cloud.openshift.util.ProcessExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KieServerWithExternalDatabaseScenarioImpl extends OpenShiftScenario implements KieServerWithExternalDatabaseScenario {

    private KieServerDeploymentImpl kieServerDeployment;
    private DockerDeployment dockerDeployment;
    private Map<String, String> envVariables;

    private static final Logger logger = LoggerFactory.getLogger(KieServerWithExternalDatabaseScenario.class);

    public KieServerWithExternalDatabaseScenarioImpl(Map<String, String> envVariables) {
        this.envVariables = envVariables;
    }

    @Override public KieServerDeployment getKieServerDeployment() {
        return kieServerDeployment;
    }

    @Override public void deploy() {
        super.deploy();

        ExternalDatabase externalDatabase = TemplateExternalDatabaseProvider.getExternalDatabase();
        envVariables.putAll(externalDatabase.getExternalDatabaseEnvironmentVariables());

        externalDatabase.getExternalDriver().ifPresent(val -> {
            ExternalDriver externalDriver = externalDatabase.getExternalDriver().get();

            dockerDeployment = DockerRegistryDeployer.deploy(project);

            URL driverBinaryUrl = OpenShiftConstants.getKieJdbcDriverBinaryUrl();

            File overrideFile = copyOverridesFileToTempLocation(externalDriver);
            adjustOverridesFileValues(overrideFile, externalDriver, driverBinaryUrl);
            installDriverImageToRegistry(dockerDeployment, externalDriver, overrideFile);
            createDriverImageStreams(dockerDeployment, externalDriver);

            envVariables.put(OpenShiftTemplateConstants.EXTENSIONS_IMAGE, externalDriver.getDockerTag());
        });

        logger.info("Processing template and creating resources from " + OpenShiftTemplate.KIE_SERVER_DATABASE_EXTERNAL.getTemplateUrl().toString());
        envVariables.put(OpenShiftTemplateConstants.IMAGE_STREAM_NAMESPACE, project.getName());
        envVariables.put(OpenShiftTemplateConstants.EXTENSIONS_IMAGE_NAMESPACE, project.getName());
        project.processTemplateAndCreateResources(OpenShiftTemplate.KIE_SERVER_DATABASE_EXTERNAL.getTemplateUrl(), envVariables);

        kieServerDeployment = new KieServerDeploymentImpl(project);
        kieServerDeployment.setUsername(DeploymentConstants.getKieServerUser());
        kieServerDeployment.setPassword(DeploymentConstants.getKieServerPassword());

        logger.info("Waiting for Kie server deployment to become ready.");
        kieServerDeployment.waitForScale();

        logNodeNameOfAllInstances();
    }

    @Override public List<Deployment> getDeployments() {
        List<Deployment> deployments = new ArrayList<Deployment>(Arrays.asList(kieServerDeployment, dockerDeployment));
        deployments.removeAll(Collections.singleton(null));
        return deployments;
    }

    @Override
    public List<WorkbenchDeployment> getWorkbenchDeployments() {
        return Collections.emptyList();
    }

    @Override
    public List<KieServerDeployment> getKieServerDeployments() {
        return Arrays.asList(kieServerDeployment);
    }

    @Override
    public List<SmartRouterDeployment> getSmartRouterDeployments() {
        return Collections.emptyList();
    }

    @Override
    public List<ControllerDeployment> getControllerDeployments() {
        return Collections.emptyList();
    }

    private File copyOverridesFileToTempLocation(ExternalDriver externalDriver) {
        try {
            File tempOverridesFile = File.createTempFile("overrides", ".yaml");
            Files.copy(externalDriver.getOverridesFileLocation().toPath(), tempOverridesFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return tempOverridesFile;
        } catch (IOException e) {
            throw new RuntimeException("Error copying overrides file.", e);
        }
    }

    private void adjustOverridesFileValues(File overridesFile, ExternalDriver externalDriver, URL driverBinaryUrl) {
        String binaryJarFileName = getBinaryJarFileName(driverBinaryUrl);
        String md5 = computeMd5(driverBinaryUrl);

        try {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            ObjectNode yamlTree = (ObjectNode) yamlMapper.readTree(overridesFile);

            yamlTree.put("name", externalDriver.getImageName());
            yamlTree.put("version", externalDriver.getImageVersion());

            ArrayNode artifacts = (ArrayNode) yamlTree.get("artifacts");
            for (JsonNode artifact : artifacts) {
                ((ObjectNode)artifact).put("url", driverBinaryUrl.toExternalForm());
                ((ObjectNode)artifact).put("md5", md5);
            }

            ArrayNode envs = (ArrayNode) yamlTree.get("envs");
            for (JsonNode env : envs) {
                if (((ObjectNode) env).get("name").equals(TextNode.valueOf("JDBC_ARTIFACT"))) {
                    ((ObjectNode) env).put("value", binaryJarFileName);
                }
            }

            String newContent = yamlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(yamlTree);
            Files.write(overridesFile.toPath(), newContent.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Error while replacing URL.", e);
        }
    }

    private String computeMd5(URL driverBinaryUrl) {
        try (InputStream is = driverBinaryUrl.openStream()) {
            return org.apache.commons.codec.digest.DigestUtils.md5Hex(is);
        } catch (IOException e) {
            throw new RuntimeException("Error while calculating MD5 digest.", e);
        }
    }

    private String getBinaryJarFileName(URL driverBinaryUrl) {
        Pattern binaryJarFilePattern = Pattern.compile(".*/([a-zA-Z0-9\\.-]*)");

        Matcher matcher = binaryJarFilePattern.matcher(driverBinaryUrl.toExternalForm());
        if (matcher.find()) {
            return matcher.group(1);
        }

        throw new RuntimeException("Failed to find binary jar file name from URL '" + driverBinaryUrl.toExternalForm() + "'.");
    }

    private void installDriverImageToRegistry(DockerDeployment dockerDeployment, ExternalDriver externalDriver, File overridesFile) {
        String dockerTag = externalDriver.getDockerTag();
        String dockerTagWithRegistry = externalDriver.getDockerTag(dockerDeployment.getUrl());

        try (ProcessExecutor processExecutor = new ProcessExecutor()) {
            logger.info("Building JDBC driver image.");
            processExecutor.executeProcessCommand("cekit build --overrides " + overridesFile.getAbsolutePath() + " --descriptor " + externalDriver.getImageDescriptorFileLocation().getAbsolutePath());

            logger.info("Pushing JDBC driver image to Docker registry.");
            processExecutor.executeProcessCommand("docker tag " + dockerTag + " " + dockerTagWithRegistry);
            processExecutor.executeProcessCommand("docker push " + dockerTagWithRegistry);
        }
    }

    private void createDriverImageStreams(DockerDeployment dockerDeployment, ExternalDriver externalDriver) {
        String imageStreamName = externalDriver.getImageName();
        String dockerTag = externalDriver.getDockerTag(dockerDeployment.getUrl());

        project.createImageStream(imageStreamName, dockerTag);
    }
}
