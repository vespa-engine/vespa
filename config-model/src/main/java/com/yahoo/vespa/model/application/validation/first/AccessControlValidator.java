// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.first;

import com.yahoo.config.model.ConfigModelContext.ApplicationType;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.Validator;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.component.Handler;

import java.util.ArrayList;
import java.util.List;

import static com.yahoo.collections.CollectionUtil.mkString;
import static com.yahoo.vespa.model.container.http.AccessControl.isBuiltinGetOnly;

/**
 * Validates that hosted applications in prod zones have write protection enabled.
 *
 * @author gjoranv
 */
public class AccessControlValidator extends Validator {

    @Override
    public void validate(VespaModel model, DeployState deployState) {

        if (! deployState.isHosted()) return;
        if (! deployState.zone().environment().isProduction()) return;
        if (model.getAdmin().getApplicationType() != ApplicationType.DEFAULT) return;

        List<String> offendingClusters = new ArrayList<>();
        for (ContainerCluster cluster : model.getContainerClusters().values()) {
            if (cluster.getHttp() == null
                    || ! cluster.getHttp().getAccessControl().isPresent()
                    || ! cluster.getHttp().getAccessControl().get().writeEnabled)

                if (hasHandlerThatNeedsProtection(cluster) || ! cluster.getAllServlets().isEmpty())
                    offendingClusters.add(cluster.getName());
        }
        if (! offendingClusters.isEmpty())
            throw new IllegalArgumentException(
                    "Access-control must be enabled for write operations to container clusters in production zones: " +
                            mkString(offendingClusters, "[", ", ", "]."));
    }

    private boolean hasHandlerThatNeedsProtection(ContainerCluster cluster) {
        return cluster.getHandlers().stream().anyMatch(this::handlerNeedsProtection);
    }

    private boolean handlerNeedsProtection(Handler<?> handler) {
        return ! isBuiltinGetOnly(handler) && hasNonMbusBinding(handler);
    }

    private boolean hasNonMbusBinding(Handler<?> handler) {
        return handler.getServerBindings().stream().anyMatch(binding -> ! binding.startsWith("mbus"));
    }
}
