// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.first;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.model.ConfigModelContext.ApplicationType;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.Validator;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.content.cluster.ContentCluster;

import java.util.ArrayList;
import java.util.List;

import static com.yahoo.collections.CollectionUtil.mkString;
import static com.yahoo.config.provision.InstanceName.defaultName;
import static com.yahoo.vespa.model.container.http.AccessControl.hasHandlerThatNeedsProtection;

/**
 * Validates that applications in prod zones do not have redundancy 1 (without a validation override).
 *
 * @author bratseth
 */
public class RedundancyOnFirstDeploymentValidator extends Validator {

    @Override
    public void validate(VespaModel model, DeployState deployState) {
        if ( ! deployState.isHosted()) return;
        if ( ! deployState.zone().environment().isProduction()) return;

        for (ContentCluster cluster : model.getContentClusters().values()) {
            if (cluster.redundancy().finalRedundancy() == 1
                && cluster.redundancy().totalNodes() > cluster.redundancy().groups())
                deployState.validationOverrides().invalid(ValidationId.redundancyOne,
                                                          cluster + " has redundancy 1, which will cause it to lose data " +
                                                          "if a node fails. This requires an override on first deployment " +
                                                          "in a production zone",
                                                          deployState.now());
        }
    }

}
