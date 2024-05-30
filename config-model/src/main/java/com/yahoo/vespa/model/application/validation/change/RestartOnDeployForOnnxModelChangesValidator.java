// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.OnnxModelCost;
import com.yahoo.vespa.model.Host;
import com.yahoo.vespa.model.application.validation.Validation.ChangeContext;
import com.yahoo.vespa.model.container.ApplicationContainer;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import static com.yahoo.config.model.api.OnnxModelCost.ModelInfo;
import static com.yahoo.vespa.model.application.validation.JvmHeapSizeValidator.gbLimit;
import static com.yahoo.vespa.model.application.validation.JvmHeapSizeValidator.percentLimit;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;

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
    public void validate(ChangeContext context) {
        if ( ! context.deployState().isHosted()) return;

        // Compare onnx models used by each cluster and set restart on deploy for cluster if estimated cost,
        // model hash or model options have changed
        for (var cluster : context.model().getContainerClusters().values()) {
            var clusterInCurrentModel = context.previousModel().getContainerClusters().get(cluster.getName());
            if (clusterInCurrentModel == null) continue;

            var currentModels = clusterInCurrentModel.onnxModelCostCalculator().models();
            var nextModels = cluster.onnxModelCostCalculator().models();

            if (enoughMemoryToAvoidRestart(clusterInCurrentModel, cluster, context.deployState().getDeployLogger()))
                continue;

            log.log(FINE, "Validating %s, current Onnx models:%s, next Onnx models:%s"
                    .formatted(cluster, currentModels, nextModels));
            validateModelChanges(cluster, currentModels, nextModels).forEach(context::require);
            validateSetOfModels(cluster, currentModels, nextModels).forEach(context::require);
        }
    }

    private List<ConfigChangeAction> validateModelChanges(ApplicationContainerCluster cluster,
                                                          Map<String, ModelInfo> currentModels,
                                                          Map<String, ModelInfo> nextModels) {
        List<ConfigChangeAction> actions = new ArrayList<>();
        for (var nextModelInfo : nextModels.values()) {
            if (! currentModels.containsKey(nextModelInfo.modelId())) continue;

            modelChanged(nextModelInfo, currentModels.get(nextModelInfo.modelId())).ifPresent(change -> {
                String message = "Onnx model '%s' has changed (%s), need to restart services in %s"
                        .formatted(nextModelInfo.modelId(), change, cluster);
                setRestartOnDeployAndAddRestartAction(actions, cluster, message);
            });
        }
        return actions;
    }

    private List<ConfigChangeAction> validateSetOfModels(ApplicationContainerCluster cluster,
                                                         Map<String, ModelInfo> currentModels,
                                                         Map<String, ModelInfo> nextModels) {
        List<ConfigChangeAction> actions = new ArrayList<>();
        Set<String> currentModelIds = currentModels.keySet();
        Set<String> nextModelIds = nextModels.keySet();
        log.log(FINE, "Checking if Onnx model set has changed (%s) -> (%s)".formatted(currentModelIds, nextModelIds));
        if (! currentModelIds.equals(nextModelIds)) {
            String message = "Onnx model set has changed from %s to %s, need to restart services in %s"
                    .formatted(currentModelIds, nextModelIds, cluster);
            setRestartOnDeployAndAddRestartAction(actions, cluster, message);
        }
        return actions;
    }

    private Optional<String> modelChanged(OnnxModelCost.ModelInfo a, OnnxModelCost.ModelInfo b) {
        log.log(FINE, "Checking if model has changed (%s) -> (%s)".formatted(a, b));
        if (a.estimatedCost() != b.estimatedCost()) return Optional.of("estimated cost");
        if (a.hash() != b.hash()) return Optional.of("model hash");
        if (! a.onnxModelOptions().equals(b.onnxModelOptions())) return Optional.of("model option(s)");
        return Optional.empty();
    }

    private static void setRestartOnDeployAndAddRestartAction(List<ConfigChangeAction> actions, ApplicationContainerCluster cluster, String message) {
        log.log(INFO, message);
        cluster.onnxModelCostCalculator().setRestartOnDeploy();
        cluster.onnxModelCostCalculator().store();
        actions.add(new VespaRestartAction(cluster.id(), message));
    }

    private static boolean enoughMemoryToAvoidRestart(ApplicationContainerCluster clusterInCurrentModel,
                                                      ApplicationContainerCluster cluster,
                                                      DeployLogger deployLogger) {
        List<ApplicationContainer> containers = cluster.getContainers();
        if (containers.isEmpty()) return true;

        double currentModelCostInGb = onnxModelCostInGb(clusterInCurrentModel);
        double nextModelCostInGb = onnxModelCostInGb(cluster);

        double totalMemory = containers.stream().mapToDouble(c -> c.getHostResource().realResources().memoryGb()).min().orElseThrow();
        double memoryUsedByModels = currentModelCostInGb + nextModelCostInGb;
        double availableMemory = Math.max(0, totalMemory - Host.memoryOverheadGb - memoryUsedByModels);

        var availableMemoryPercentage = cluster.heapSizePercentageOfAvailable();
        int memoryPercentage = (int) (availableMemory / totalMemory * availableMemoryPercentage);

        var prefix = "Validating Onnx models memory usage for %s".formatted(cluster);
        if (memoryPercentage < percentLimit) {
            deployLogger.log(INFO, ("%s, percentage of available memory " +
                    "too low (%d < %d) to avoid restart, consider a flavor with more memory to avoid this")
                    .formatted(prefix, memoryPercentage, percentLimit));
            return false;
        }

        if (availableMemory < gbLimit) {
            deployLogger.log(INFO, ("%s, available memory too low "
                             + "(%.2f Gb < %.2f Gb) to avoid restart, consider a flavor with more memory to avoid this")
                    .formatted(prefix, availableMemory, gbLimit));
            return false;
        }

        log.log(FINE, "%s, enough available memory (%.2f Gb) to avoid restart (models use %.2f Gb)"
                .formatted(prefix, availableMemory, memoryUsedByModels));
        return true;
    }

    private static double onnxModelCostInGb(ApplicationContainerCluster clusterInCurrentModel) {
        return (double) clusterInCurrentModel.onnxModelCostCalculator().aggregatedModelCostInBytes() / 1024 / 1024 / 1024;
    }

}
