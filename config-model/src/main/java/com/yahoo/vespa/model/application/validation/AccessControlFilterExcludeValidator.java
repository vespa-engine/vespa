// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.provision.CloudName;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.http.AccessControl;
import com.yahoo.vespa.model.container.http.Http;

import java.util.logging.Level;

/**
 * Validates that 'access-control' does not include any exclusions unless explicitly allowed.
 * Logs in Yahoo clouds and fails in AWS clouds
 *
 * @author mortent
 */
public class AccessControlFilterExcludeValidator extends Validator {

    @Override
    public void validate(VespaModel model, DeployState deployState) {
        if (!deployState.isHosted() || deployState.zone().system().isPublic()) return;
        if (deployState.getProperties().allowDisableMtls()) return;
        model.getContainerClusters().forEach((id, cluster) -> {
            Http http = cluster.getHttp();
            if (http != null) {
                if (http.getAccessControl().isPresent()) {
                    verifyNoExclusions(id, http.getAccessControl().get(), deployState);
                }
            }
        });
    }

    private void verifyNoExclusions(String clusterId, AccessControl accessControl, DeployState deployState) {
        if (!accessControl.excludedBindings().isEmpty()) {
            String message = "Application cluster %s excludes paths from access control, this is not allowed and should be removed.".formatted(clusterId);
            if (deployState.zone().cloud().name().equals(CloudName.AWS)) {
                throw new IllegalArgumentException(message);
            } else {
                deployState.getDeployLogger().log(Level.WARNING, message);
            }
        }
   }
}
