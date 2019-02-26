/*
 * Copyright 2018 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.cloud.openshift.deployment;

import java.net.MalformedURLException;
import java.net.URL;

import org.kie.cloud.api.deployment.NexusDeployment;
import org.kie.cloud.openshift.resource.Project;

public class NexusDeploymentImpl extends OpenShiftDeployment implements NexusDeployment {

    private String serviceName;
    private URL url;

    public NexusDeploymentImpl(Project project) {
        super(project);
    }

    @Override
    public String getServiceName() {
        if (serviceName == null) {
            serviceName = ServiceUtil.getNexusServiceName(getOpenShiftUtil());
        }
        return serviceName;
    }

    @Override
    public URL getSnapshotRepoUrl() {
        try {
            return new URL (getUrl().toExternalForm() + "/repository/maven-snapshots");
        } catch (MalformedURLException e) {
            throw new RuntimeException("Malformed URL when constructing snapshot Maven repo URL.", e);
        }
    }

    @Override
    public URL getReleasedRepoUrl() {
        try {
            return new URL (getUrl().toExternalForm() + "/repository/maven-releases");
        } catch (MalformedURLException e) {
            throw new RuntimeException("Malformed URL when constructing released Maven repo URL.", e);
        }
    }

    private URL getUrl() {
        if (url == null) {
            url = getHttpRouteUrl(getServiceName()).orElseThrow(() -> new RuntimeException("No Nexus URL is available."));
        }
        return url;
    }

    @Override
    public void waitForScale() {
        super.waitForScale();
        if (getInstances().size() > 0) {
            RouterUtil.waitForRouter(getUrl());
        }
    }
}
