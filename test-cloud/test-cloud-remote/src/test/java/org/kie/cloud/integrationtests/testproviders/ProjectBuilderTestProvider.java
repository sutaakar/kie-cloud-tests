/*
 * Copyright 2018 Red Hat, Inc. and/or its affiliates.
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
package org.kie.cloud.integrationtests.testproviders;

import java.util.Objects;

import org.kie.cloud.api.deployment.KieServerDeployment;
import org.kie.cloud.api.deployment.WorkbenchDeployment;
import org.kie.cloud.api.scenario.DeploymentScenario;
import org.kie.cloud.common.provider.KieServerClientProvider;
import org.kie.cloud.common.provider.WorkbenchClientProvider;
import org.kie.cloud.tests.common.client.util.WorkbenchUtils;
import org.kie.server.api.model.KieContainerResource;
import org.kie.server.api.model.ReleaseId;
import org.kie.server.api.model.ServiceResponse;
import org.kie.server.client.KieServicesClient;
import org.kie.server.integrationtests.shared.KieServerAssert;
import org.kie.wb.test.rest.client.WorkbenchClient;

public class ProjectBuilderTestProvider {

    private ProjectBuilderTestProvider() {}

    /**
     * Create provider instance
     * 
     * @return provider instance
     */
    public static ProjectBuilderTestProvider create() {
        return create(null);
    }

    /**
     * Create provider instance and init it with given environment
     * 
     * @param environment if not null, initialize this provider with the environment
     * 
     * @return provider instance
     */
    public static ProjectBuilderTestProvider create(DeploymentScenario<?> deploymentScenario) {
        ProjectBuilderTestProvider provider = new ProjectBuilderTestProvider();
        if (Objects.nonNull(deploymentScenario)) {
            provider.init(deploymentScenario);
        }
        return provider;
    }

    private void init(DeploymentScenario<?> deploymentScenario) {}

    public void testCreateAndDeployProject(WorkbenchDeployment workbenchDeployment,
                                           KieServerDeployment kieServerDeployment) {

        final String spaceName = "testBuildProject-space";
        WorkbenchClient workbenchClient = WorkbenchClientProvider.getWorkbenchClient(workbenchDeployment);
        workbenchClient.createSpace(spaceName, workbenchDeployment.getUsername());
        WorkbenchUtils.waitUntilSpaceIsSynchronised(workbenchDeployment, spaceName);
        final String projectName = "testBuildProject-project";
        final String projectVersion = "1.0";
        workbenchClient.createProject(spaceName, projectName, workbenchClient.getSpace(spaceName).getDefaultGroupId(),
                                      projectVersion);

        workbenchClient.deployProject(spaceName, projectName);

        String containerId = "testBuildProject-id";
        KieServicesClient kieServerClient = KieServerClientProvider.getKieServerClient(kieServerDeployment);

        ServiceResponse<KieContainerResource> createContainer = kieServerClient.createContainer(containerId,
                                                                                                new KieContainerResource(containerId, new ReleaseId(
                                                                                                                                                    workbenchClient.getSpace(spaceName).getDefaultGroupId(), projectName,
                                                                                                                                                    projectVersion)));
        KieServerAssert.assertSuccess(createContainer);
        kieServerDeployment.waitForContainerRespin();
    }
}
