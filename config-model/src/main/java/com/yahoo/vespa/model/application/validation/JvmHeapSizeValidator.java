// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.text.Text;
import com.yahoo.vespa.model.VespaModel;

import java.util.logging.Level;

/**
 * Validates that the container node flavour has enough resources to run configured ONNX models.
 *
 * @author bjorncs
 */
public class JvmHeapSizeValidator extends Validator {

    public static final int percentLimit = 15;
    public static final double gbLimit = 0.6;

    @Override
    public void validate(VespaModel model, DeployState ds) {
        if (!ds.featureFlags().dynamicHeapSize()) return;
        if (!ds.isHostedTenantApplication(model.getAdmin().getApplicationType())) return;

        model.getContainerClusters().forEach((clusterId, appCluster) -> {
            var mp = appCluster.getMemoryPercentage().orElse(null);
            if (mp == null) return;
            if (mp.availableMemoryGb().isEmpty()) {
                ds.getDeployLogger().log(Level.FINE, "Host resources unknown or percentage overridden with 'allocated-memory'");
                return;
            }
            long jvmModelCost = appCluster.onnxModelCostCalculator().aggregatedModelCostInBytes();
            if (jvmModelCost > 0) {
                double availableMemoryGb = mp.availableMemoryGb().getAsDouble();
                double modelCostGb = jvmModelCost / (1024D * 1024 * 1024);
                ds.getDeployLogger().log(Level.FINE, () -> Text.format("JVM: %d%% (limit: %d%%), %.2fGB (limit: %.2fGB), ONNX: %.2fGB",
                        mp.percentage(), percentLimit, availableMemoryGb, gbLimit, modelCostGb));
                if (mp.percentage() < percentLimit) {
                    throw new IllegalArgumentException(Text.format("Allocated percentage of memory of JVM in cluster '%s' is too low (%d%% < %d%%). " +
                                    "Estimated cost of ONNX models is %.2fGB. Either use a node flavor with more memory or use less expensive models. " +
                                    "You may override this validation by specifying 'allocated-memory' (https://docs.vespa.ai/en/performance/container-tuning.html#jvm-heap-size).",
                            clusterId, mp.percentage(), percentLimit, modelCostGb));
                }
                if (availableMemoryGb < gbLimit) {
                    throw new IllegalArgumentException(
                            Text.format("Allocated memory to JVM in cluster '%s' is too low (%.2fGB < %.2fGB). " +
                                    "Estimated cost of ONNX models is %.2fGB. Either use a node flavor with more memory or use less expensive models. " +
                                    "You may override this validation by specifying 'allocated-memory' (https://docs.vespa.ai/en/performance/container-tuning.html#jvm-heap-size).",
                                            clusterId, availableMemoryGb, gbLimit, modelCostGb));
                }
            }
        });
    }
}
