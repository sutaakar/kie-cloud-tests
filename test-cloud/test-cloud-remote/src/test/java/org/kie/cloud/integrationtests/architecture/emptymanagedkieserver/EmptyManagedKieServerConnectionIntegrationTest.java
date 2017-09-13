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

package org.kie.cloud.integrationtests.architecture.emptymanagedkieserver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.kie.cloud.api.DeploymentScenarioBuilderFactory;
import org.kie.cloud.api.scenario.WorkbenchRuntimeSmartRouterKieServerDatabaseScenario;
import org.kie.cloud.common.provider.KieServerClientProvider;
import org.kie.cloud.common.provider.KieServerControllerClientProvider;
import org.kie.cloud.integrationtests.AbstractCloudIntegrationTest;
import org.kie.cloud.maven.MavenDeployer;
import org.kie.cloud.maven.constants.MavenConstants;
import org.kie.server.api.model.KieContainerResourceList;
import org.kie.server.api.model.KieContainerStatus;
import org.kie.server.api.model.KieServerInfo;
import org.kie.server.api.model.ServiceResponse;
import org.kie.server.client.KieServicesClient;
import org.kie.server.controller.api.model.spec.ContainerSpec;
import org.kie.server.controller.api.model.spec.ServerTemplate;
import org.kie.server.integrationtests.controller.client.KieServerMgmtControllerClient;

public class EmptyManagedKieServerConnectionIntegrationTest extends AbstractCloudIntegrationTest<WorkbenchRuntimeSmartRouterKieServerDatabaseScenario> {

    private static final String KIE_SERVER_ID = "test-kie-server";
    private static final String SMART_ROUTER_ID = "test-kie-router";

    private KieServerMgmtControllerClient kieControllerClient;
    private KieServicesClient kieServerClient;
    private KieServicesClient smartRouterClient;

    @Override
    protected WorkbenchRuntimeSmartRouterKieServerDatabaseScenario createDeploymentScenario(DeploymentScenarioBuilderFactory deploymentScenarioFactory) {
        return deploymentScenarioFactory.getWorkbenchRuntimeSmartRouterKieServerDatabaseScenarioBuilder()
                .withExternalMavenRepo(MavenConstants.getMavenRepoUrl(), MavenConstants.getMavenRepoUser(), MavenConstants.getMavenRepoPassword())
                .withKieServerId(KIE_SERVER_ID)
                .withSmartRouterId(SMART_ROUTER_ID)
                .build();
    }

    @Before
    public void setUp() {
        MavenDeployer.buildAndDeployMavenProject(ClassLoader.class.getResource("/kjars-sources/definition-project-snapshot").getFile());

        kieControllerClient = KieServerControllerClientProvider.getKieServerMgmtControllerClient(deploymentScenario.getWorkbenchDeployment());
        kieServerClient = KieServerClientProvider.getKieServerClient(deploymentScenario.getKieServerDeployment());
        smartRouterClient = KieServerClientProvider.getKieServerClient(deploymentScenario.getSmartRouterDeployment().getUrl(), deploymentScenario.getKieServerDeployment());
    }

    @Test
    public void testConnectionBetweenDeployables() {
        List<String> serverTemplateIds = kieControllerClient.listServerTemplates().stream().map(ServerTemplate::getId).collect(Collectors.toList());
        assertThat(serverTemplateIds).as("Kie server isn't registered in controller.").contains(KIE_SERVER_ID);
        // TODO uncomment when https://github.com/jboss-openshift/cct_module/pull/84 becomes available in the image
//        assertThat(serverTemplateIds).as("Smart router isn't registered in controller.").contains(SMART_ROUTER_ID);

        // Deploy container
        KieServerInfo serverInfo = kieServerClient.getServerInfo().getResult();
        kieControllerClient.saveContainerSpec(serverInfo.getServerId(), serverInfo.getName(), CONTAINER_ID, CONTAINER_ALIAS, PROJECT_GROUP_ID, DEFINITION_PROJECT_SNAPSHOT_NAME, DEFINITION_PROJECT_SNAPSHOT_VERSION, KieContainerStatus.STARTED);
        KieServerClientProvider.waitForContainerStart(deploymentScenario.getKieServerDeployment(), CONTAINER_ID);

        verifyContainerIsDeployed(kieServerClient, CONTAINER_ID);
        verifyContainerIsDeployed(smartRouterClient, CONTAINER_ID);
        verifyServerTemplateContainsContainer(KIE_SERVER_ID, CONTAINER_ID);
        // TODO uncomment when https://github.com/jboss-openshift/cct_module/pull/84 becomes available in the image
//        verifyServerTemplateContainsContainer(SMART_ROUTER_ID, CONTAINER_ID);
    }

    private void verifyContainerIsDeployed(KieServicesClient kieServerClient, String containerId) {
        ServiceResponse<KieContainerResourceList> containers = kieServerClient.listContainers();
        assertThat(containers.getType()).isEqualTo(ServiceResponse.ResponseType.SUCCESS);

        assertThat(containers.getResult().getContainers()).hasSize(1);
        assertThat(containers.getResult().getContainers().get(0).getContainerId()).isEqualTo(containerId);
        assertThat(containers.getResult().getContainers().get(0).getStatus()).isEqualTo(KieContainerStatus.STARTED);
    }

    private void verifyServerTemplateContainsContainer(String serverTemplate, String containerId) {
        Collection<ContainerSpec> containersSpec = kieControllerClient.getServerTemplate(serverTemplate).getContainersSpec();
        assertThat(containersSpec).hasSize(1);
        assertThat(containersSpec.iterator().next().getId()).isEqualTo(containerId);
    }
}
