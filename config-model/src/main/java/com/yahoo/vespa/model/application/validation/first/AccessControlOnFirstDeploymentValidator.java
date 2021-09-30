// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.first;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.model.ConfigModelContext.ApplicationType;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.Validator;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.ContainerCluster;

import java.util.ArrayList;
import java.util.List;

import static com.yahoo.collections.CollectionUtil.mkString;
import static com.yahoo.config.provision.InstanceName.defaultName;
import static com.yahoo.vespa.model.container.http.AccessControl.hasHandlerThatNeedsProtection;

/**
 * Validates that hosted applications in prod zones have write protection enabled.
 *
 * @author gjoranv
 */
public class AccessControlOnFirstDeploymentValidator extends Validator {

    @Override
    public void validate(VespaModel model, DeployState deployState) {

        if (! needsAccessControlValidation(model, deployState)) return;

        List<String> offendingClusters = new ArrayList<>();
        for (ContainerCluster<? extends Container> c : model.getContainerClusters().values()) {
            if (! (c instanceof ApplicationContainerCluster)) continue;
            ApplicationContainerCluster cluster = (ApplicationContainerCluster)c;
            if (cluster.getHttp() == null
                    || ! cluster.getHttp().getAccessControl().isPresent()
                    || ! cluster.getHttp().getAccessControl().get().writeEnabled)

                if (hasHandlerThatNeedsProtection(cluster) || ! cluster.getAllServlets().isEmpty())
                    offendingClusters.add(cluster.getName());
        }
        if (! offendingClusters.isEmpty())
            deployState.validationOverrides().invalid(ValidationId.accessControl,
                                                      "Access-control must be enabled for write operations to container clusters in production zones: " +
                                                              mkString(offendingClusters, "[", ", ", "]"), deployState.now());
    }

    public static boolean needsAccessControlValidation(VespaModel model, DeployState deployState) {
        if (! deployState.isHosted()) return false;
        if (! deployState.zone().environment().isProduction()) return false;
        if (deployState.zone().system().isPublic()) return false;
        if (! deployState.getApplicationPackage().getApplicationId().instance().equals(defaultName())) return false;
        if (model.getAdmin().getApplicationType() != ApplicationType.DEFAULT) return false;

        return true;
    }
}
