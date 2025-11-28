// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import ai.vespa.utils.BytesQuantity;
import com.yahoo.text.Text;
import com.yahoo.vespa.model.application.validation.Validation.Context;

/**
 * Validates that the configured inference memory does not exceed available node memory.
 *
 * @author glebashnik
 */
public class InferenceMemoryValidator implements Validator {

    @Override
    public void validate(Context context) {
        context.model().getContainerClusters().forEach((clusterId, cluster) -> {
            var inferenceMemoryBytes = cluster.getInferenceMemoryBytes();

            if (inferenceMemoryBytes.isEmpty() || cluster.getContainers().isEmpty()) {
                return;
            }

            var nodeMemoryGb = cluster.getContainers().stream()
                    .filter(container -> container.getHostResource() != null)
                    .mapToDouble(container ->
                            container.getHostResource().realResources().memoryGiB())
                    .min()
                    .orElse(Double.MAX_VALUE);

            if (nodeMemoryGb <= 0 || nodeMemoryGb == Double.MAX_VALUE) {
                return;
            }

            var inferenceMemoryGb = BytesQuantity.bytesToGB(inferenceMemoryBytes.get());

            if (inferenceMemoryGb > nodeMemoryGb) {
                context.illegal(Text.format(
                        "Inference memory in cluster '%s' (%.2f GiB) cannot exceed available node memory (%.2f GiB)."
                            + " Either use a node flavor with more memory or reduce the inference memory allocation.",
                        clusterId, inferenceMemoryGb, nodeMemoryGb));
            }
        });
    }
}
