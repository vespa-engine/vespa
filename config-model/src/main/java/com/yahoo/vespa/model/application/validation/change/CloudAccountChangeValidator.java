// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.model.VespaModel;

import java.util.List;

/**
 * @author mpolden
 */
public class CloudAccountChangeValidator implements ChangeValidator {

    @Override
    public List<ConfigChangeAction> validate(VespaModel current, VespaModel next, DeployState deployState) {
        for (var clusterId : current.allClusters()) {
            CloudAccount currentAccount = cloudAccountOf(current, clusterId);
            CloudAccount nextAccount = cloudAccountOf(next, clusterId);
            if (currentAccount == null || nextAccount == null) continue;

            if (!nextAccount.equals(currentAccount)) {
                throw new IllegalArgumentException("Cannot change cloud account from " + currentAccount +
                                                   " to " + nextAccount + ". The existing deployment must be removed " +
                                                   "before changing accounts");
            }
        }
        return List.of();
    }

    private static CloudAccount cloudAccountOf(VespaModel model, ClusterSpec.Id cluster) {
        Capacity capacity = model.provisioned().all().get(cluster);
        return capacity == null ? null : capacity.cloudAccount().orElse(CloudAccount.empty);
    }

}
