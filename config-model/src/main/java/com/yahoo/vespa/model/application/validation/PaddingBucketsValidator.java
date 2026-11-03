// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import ai.vespa.modelintegration.evaluator.config.OnnxEvaluatorConfig;

import java.util.Optional;
import java.util.logging.Level;

/**
 * Warns about padding buckets the runtime will not use: it pads only when batching on a GPU, where padding is worthwhile.
 * Checks the evaluator config the components produce, which is what the runtime sees.
 *
 * @author ingridhabrekke
 * @author glebashnik
 */
public class PaddingBucketsValidator implements Validator {

    @Override
    public void validate(Validation.Context context) {
        context.model().getContainerClusters().forEach((__, cluster) -> {
            for (var component : cluster.getAllComponents()) {
                if ( ! (component instanceof OnnxEvaluatorConfig.Producer producer)) continue;
                var config = new OnnxEvaluatorConfig.Builder();
                producer.getConfig(config);
                reasonPaddingHasNoEffect(config.build()).ifPresent(reason ->
                        context.deployState().getDeployLogger().logApplicationPackage(Level.WARNING,
                                "For component '" + component.getComponentId() + "': padding-buckets has no effect since " +
                                reason + ". Padding requires a batching max-size above 1 on a GPU"));
            }
        });
    }

    /**
     * Returns why configured padding buckets will not be used, if so. The GPU device defaults to 0, so a model
     * without onnx-gpu-device may be placed on a GPU. With a model config override they are always used.
     */
    static Optional<String> reasonPaddingHasNoEffect(OnnxEvaluatorConfig config) {
        if (config.batching().paddingBuckets().isEmpty() || config.modelConfigOverride().isPresent()) return Optional.empty();
        if (config.batching().maxSize() <= 1)
            return Optional.of("the batching max-size is not above 1");
        if (config.gpuDevice() < 0)
            return Optional.of("onnx-gpu-device is -1, which means CPU");
        return Optional.empty();
    }

}
