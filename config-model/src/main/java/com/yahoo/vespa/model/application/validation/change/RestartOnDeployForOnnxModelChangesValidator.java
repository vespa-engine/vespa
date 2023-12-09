// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.OnnxModelCost;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.VespaModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import static java.util.logging.Level.FINE;

/**
 * If Onnx models change in a way that requires restart of containers in
 * a container cluster this validator will make sure that restartOnDeploy is set for
 * configs for this cluster.
 *
 * @author hmusum
 */
public class RestartOnDeployForOnnxModelChangesValidator implements ChangeValidator {

    private static final Logger log = Logger.getLogger(RestartOnDeployForOnnxModelChangesValidator.class.getName());

    @Override
    public List<ConfigChangeAction> validate(VespaModel currentModel, VespaModel nextModel, DeployState deployState) {
        if ( ! deployState.featureFlags().restartOnDeployWhenOnnxModelChanges()) return List.of();
        List<ConfigChangeAction> actions = new ArrayList<>();

        // Compare onnx models used by each cluster and set restart on deploy for cluster if estimated cost,
        // model hash or model options have changed
        // TODO: Skip if container has enough memory to handle reload of onnx model (2 models in memory at the same time)

        for (var cluster : nextModel.getContainerClusters().values()) {
            var clusterInCurrentModel = currentModel.getContainerClusters().get(cluster.getName());
            if (clusterInCurrentModel == null) continue;

            log.log(FINE, "Validating cluster '" + cluster.name() + "'");
            var currentModels = clusterInCurrentModel.onnxModelCostCalculator().models();
            var nextModels = cluster.onnxModelCostCalculator().models();
            log.log(FINE, "current models=" + currentModels + ",  next models=" + nextModels);

            for (var nextModelInfo : nextModels.values()) {
                if (!currentModels.containsKey(nextModelInfo.modelId())) continue;

                log.log(FINE, "Checking if " + nextModelInfo + " has changed");
                modelChanged(nextModelInfo, currentModels.get(nextModelInfo.modelId())).ifPresent(change -> {
                    String message = "Onnx model '%s' has changed (%s), need to restart services in container cluster '%s'"
                            .formatted(nextModelInfo.modelId(), change, cluster.name());
                    cluster.onnxModelCostCalculator().setRestartOnDeploy();
                    actions.add(new VespaRestartAction(cluster.id(), message));
                });
            }
        }
        return actions;
    }

    private Optional<String> modelChanged(OnnxModelCost.ModelInfo a, OnnxModelCost.ModelInfo b) {
        if (a.estimatedCost() != b.estimatedCost()) return Optional.of("estimated cost");
        if (a.hash() != b.hash()) return Optional.of("model hash");
        if (a.onnxModelOptions().isPresent() && b.onnxModelOptions().isPresent()
                && ! a.onnxModelOptions().get().equals(b.onnxModelOptions().get()))
            return Optional.of("model option(s)");
        return Optional.empty();
    }

}
