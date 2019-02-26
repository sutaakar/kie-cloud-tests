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

package org.kie.cloud.maven;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import cz.xtf.maven.MavenUtil;
import org.apache.maven.it.VerificationException;
import org.kie.cloud.api.constants.ConfigurationInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MavenDeployer {

    private static final Logger logger = LoggerFactory.getLogger(MavenDeployer.class);

    private static final String SETTINGS_XML_PATH = System.getProperty("kjars.build.settings.xml");

    static {
        ConfigurationInitializer.initConfigProperties();
    }

    /**
     * Build Maven project from specified directory using maven command "clean install".
     *
     * @param basedir Directory to build a project from.
     */
    public static void buildAndInstallMavenProject(String basedir) {
        buildMavenProject(basedir, "install", null);
    }

    /**
     * Build Maven project from specified directory using maven command "clean deploy".
     *
     * @param basedir Directory to build a project from.
     */
    public static void buildAndDeployMavenProject(String basedir) {
        buildMavenProject(basedir, "deploy", null);
    }

    /**
     * Build Maven project from specified directory using maven command "clean deploy" to repository defined in parameters.
     *
     * @param basedir Directory to build a project from.
     * @param repositoryUrl URL of the target repository
     */
    public static void buildAndDeployMavenProjectToRepository(String basedir, URL repositoryUrl) {
        buildMavenProject(basedir, "deploy", repositoryUrl);
    }

    /**
     * Build Maven project from specified directory using maven command from parameter.
     *
     * @param basedir Directory to build a project from.
     * @param buildCommand Build command, for example "install" or "deploy".
     * @param repositoryUrl URL of the target repository or null if repository not specified
     */
    private static void buildMavenProject(String basedir, String buildCommand, URL repositoryUrl) {
        try {
            MavenUtil mavenUtil = MavenUtil.forProject(Paths.get(basedir)).forkJvm();
            addSettingsXmlPathIfExists(mavenUtil);
            addTargetRepositoryIfExists(mavenUtil, repositoryUrl);
            mavenUtil.executeGoals(buildCommand);

            logger.debug("Maven project successfully built and deployed!");
        } catch (VerificationException e) {
            throw new RuntimeException("Error while building Maven project from basedir " + basedir, e);
        }
    }

    /**
     * Add settings.xml file to maven build if it was defined and exists.
     *
     * @param mavenUtil
     */
    private static void addSettingsXmlPathIfExists(MavenUtil mavenUtil) {
        if (SETTINGS_XML_PATH != null && !SETTINGS_XML_PATH.isEmpty()) {
            Path settingsXmlPath = Paths.get(SETTINGS_XML_PATH);
            if (settingsXmlPath.toFile().exists()) {
                mavenUtil.useSettingsXml(settingsXmlPath);
            } else {
                throw new RuntimeException("Path to settings.xml file with value " + SETTINGS_XML_PATH + " points to non existing location.");
            }
        }
    }

    /**
     * Add target repository to install the artifact to.
     *
     * @param mavenUtil
     * @param repositoryUrl
     */
    private static void addTargetRepositoryIfExists(MavenUtil mavenUtil, URL repositoryUrl) {
        if (repositoryUrl != null) {
            mavenUtil.deployToRepository("remote-testing-repo", repositoryUrl.toExternalForm());
        }
    }
}
