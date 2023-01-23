// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.first;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.Validator;
import com.yahoo.vespa.model.application.validation.change.ChangeValidator;
import com.yahoo.vespa.model.content.cluster.ContentCluster;

import java.time.Instant;
import java.util.List;

/**
 * Validates that applications in prod zones do not have redundancy 1 (without a validation override).
 *
 * @author bratseth
 */
public class RedundancyValidator extends Validator implements ChangeValidator {

    /** Validate on first deployment. */
    @Override
    public void validate(VespaModel model, DeployState deployState) {
        if ( ! deployState.isHosted()) return;
        if ( ! deployState.zone().environment().isProduction()) return;

        for (ContentCluster cluster : model.getContentClusters().values()) {
            if (cluster.redundancy().finalRedundancy() == 1 && cluster.redundancy().groups() == 1)
                deployState.validationOverrides().invalid(ValidationId.redundancyOne,
                                                          cluster + " has redundancy 1, which will cause it to lose data " +
                                                          "if a node fails. This requires an override on first deployment " +
                                                          "in a production zone",
                                                          deployState.now());
        }
    }

    /** Validate on change. */
    @Override
    public List<ConfigChangeAction> validate(VespaModel current, VespaModel next, DeployState deployState) {
        return List.of();
    }

}
