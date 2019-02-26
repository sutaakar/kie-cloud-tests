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

package org.kie.cloud.openshift.scenario.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.kie.cloud.api.scenario.DeploymentScenario;
import org.kie.cloud.api.scenario.builder.DeploymentScenarioBuilder;
import org.kie.cloud.api.scenario.builder.KieDeploymentScenarioBuilder;

public abstract class KieScenarioBuilderImpl<T extends DeploymentScenarioBuilder<U>, U extends DeploymentScenario<U>> implements KieDeploymentScenarioBuilder<T, U> {

    protected boolean deployNexus = false;
    protected List<Consumer<U>> preDeploymentListeners = new ArrayList<>();

    @Override
    @SuppressWarnings("unchecked")
    public T withNexusMavenRepoDeployedInAdvance() {
        deployNexus = true;
        preDeploymentListeners.add(scenario -> {
            scenario.getNexusDeployment().ifPresent(nexusDeployment -> {
                nexusDeployment.waitForScale();
            });
        });
        return (T) this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T withPreDeploymentListener(Consumer<U> listener) {
        preDeploymentListeners.add(listener);
        return (T) this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public U build() {
        DeploymentScenario<U> deploymentScenarioInstance = getDeploymentScenarioInstance();

        if (deployNexus) {
            deploymentScenarioInstance.deployNexus();
        }

        for (Consumer<U> preDeploymentListener : preDeploymentListeners) {
            deploymentScenarioInstance.addPreDeploymentListener(preDeploymentListener);
        }

        return (U) deploymentScenarioInstance;
    }

    protected abstract DeploymentScenario<U> getDeploymentScenarioInstance();
}
