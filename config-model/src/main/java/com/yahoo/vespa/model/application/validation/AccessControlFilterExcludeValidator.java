// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.provision.CloudName;
import com.yahoo.vespa.model.application.validation.Validation.Context;
import com.yahoo.vespa.model.container.http.AccessControl;
import com.yahoo.vespa.model.container.http.Http;

import java.util.logging.Level;

/**
 * Validates that 'access-control' does not include any exclusions unless explicitly allowed.
 * Logs in Yahoo clouds and fails in AWS clouds
 *
 * @author mortent
 */
public class AccessControlFilterExcludeValidator implements Validator {

    @Override
    public void validate(Context context) {
        if (!context.deployState().isHosted() || context.deployState().zone().system().isPublic()) return;
        if (context.deployState().getProperties().allowDisableMtls()) return;
        context.model().getContainerClusters().forEach((id, cluster) -> {
            Http http = cluster.getHttp();
            if (http != null) {
                if (http.getAccessControl().isPresent()) {
                    verifyNoExclusions(id, http.getAccessControl().get(), context);
                }
            }
        });
    }

    private void verifyNoExclusions(String clusterId, AccessControl accessControl, Context context) {
        if (!accessControl.excludedBindings().isEmpty()) {
            String message = "Application cluster %s excludes paths from access control, this is not allowed and should be removed.".formatted(clusterId);
            if (context.deployState().zone().cloud().name().equals(CloudName.AWS)) {
                context.illegal(message);
            } else {
                context.deployState().getDeployLogger().log(Level.WARNING, message);
            }
        }
   }
}
