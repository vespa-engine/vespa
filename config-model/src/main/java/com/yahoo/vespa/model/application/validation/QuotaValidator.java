package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.model.VespaModel;

import java.util.stream.Collectors;

public class QuotaValidator extends Validator {
    @Override
    public void validate(VespaModel model, DeployState deployState) {
        var quota = deployState.getProperties().quota();
        quota.maxClusterSize().ifPresent(maxClusterSize -> validateMaxClusterSize(maxClusterSize, model));
    }

    private void validateMaxClusterSize(int maxClusterSize, VespaModel model) {
        var invalidClusters = model.allClusters().stream()
                .filter(clusterId -> {
                    var cluster = model.provisioned().all().get(clusterId);
                    var clusterSize = cluster.maxResources().nodes();
                    return clusterSize > maxClusterSize;
                })
                .map(ClusterSpec.Id::value)
                .collect(Collectors.toList());

        if (!invalidClusters.isEmpty()) {
            // TODO(ogronnesby): Find better exception
            var clusterNames = String.join(", ", invalidClusters);
            throw new RuntimeException("Clusters " + clusterNames + " exceeded max cluster size of " + maxClusterSize);
        }
    }
}
