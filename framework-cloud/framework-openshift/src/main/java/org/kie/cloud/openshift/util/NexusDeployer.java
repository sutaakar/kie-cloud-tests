/*
 * Copyright 2019 JBoss by Red Hat.
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
package org.kie.cloud.openshift.util;

import java.io.IOException;

import cz.xtf.openshift.OpenShiftBinaryClient;
import org.kie.cloud.api.deployment.NexusDeployment;
import org.kie.cloud.openshift.deployment.NexusDeploymentImpl;
import org.kie.cloud.openshift.resource.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class used for deploying Nexus Maven repo to OpenShift project.
 */
public class NexusDeployer {

    private static final Logger logger = LoggerFactory.getLogger(NexusDeployer.class);

    public static NexusDeployment deploy(Project project) {
        deployNexusMavenRepository(project);

        NexusDeployment nexusDeployment = new NexusDeploymentImpl(project);
        return nexusDeployment;
    }

    private static void deployNexusMavenRepository(Project project) {
        logger.info("Creating internal Nexus Maven repository.");

        try {
            OpenShiftBinaryClient.getInstance().loginDefault();
        } catch (IOException e) {
            throw new RuntimeException("Error while logging into OpenShift using XTF.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while logging into OpenShift using XTF.", e);
        }

        OpenShiftBinaryClient.getInstance().project(project.getName());
        OpenShiftBinaryClient.getInstance().executeCommand("Nexus Maven repository creation failed.", "new-app", "sonatype/nexus3", "-l", "deploymentConfig=nexus");
        OpenShiftBinaryClient.getInstance().executeCommand("Nexus Maven repository exposing failed.", "expose", "service", "nexus3");
    }
}
