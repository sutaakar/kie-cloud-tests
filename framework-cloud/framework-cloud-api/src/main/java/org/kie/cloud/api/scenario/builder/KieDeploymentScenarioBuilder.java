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

package org.kie.cloud.api.scenario.builder;

import java.util.function.Consumer;

import org.kie.cloud.api.scenario.DeploymentScenario;
import org.kie.cloud.api.scenario.KieDeploymentScenario;

/**
 * Cloud deployment scenario builder. Create setup for Deployment scenario.
 *
 * @see KieDeploymentScenario
 *
 * @param <T> Setup to be built e.g. WorkbenchKieServerDatabaseScenario
 * @see org.kie.cloud.api.scenario.WorkbenchKieServerDatabaseScenario
 */
public interface KieDeploymentScenarioBuilder<T extends DeploymentScenarioBuilder<U>, U extends DeploymentScenario<U>> extends DeploymentScenarioBuilder<U> {

    /**
     * Return setup builder with additional configuration of Nexus Maven repo. This repository is deployed before of the Kie artifact deployment.
     * @return Builder with configured Nexus Maven repo.
     */
    T withNexusMavenRepoDeployedInAdvance();

    /**
     * Return setup builder with additional pre deployment listener. This listener is triggered before the Kie deployments are created.
     * @param listener Scenario listener
     * @return Builder with additional pre deployment listener.
     */
    T withPreDeploymentListener(Consumer<U> listener);
}
