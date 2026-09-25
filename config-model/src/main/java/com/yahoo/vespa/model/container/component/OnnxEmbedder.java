// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.config.model.api.OnnxModelOptions;
import com.yahoo.config.model.deploy.DeployState;
import ai.vespa.modelintegration.evaluator.config.OnnxEvaluatorConfig;
import com.yahoo.text.XML;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Optional;

import static com.yahoo.text.XML.getChild;
import static com.yahoo.text.XML.getChildValue;


/**
 * Base class for embedders using ONNX models.
 * Extracts common ONNX parameters from XML to construct {@link OnnxModelOptions} and {@link OnnxEvaluatorConfig}.
 * 
 * @author glebashnik
 */
abstract class OnnxEmbedder extends TypedComponent implements OnnxEvaluatorConfig.Producer {
    final protected OnnxModelOptions onnxModelOptions;
    private final boolean forceDisableOptimization;

    @SuppressWarnings("removal") // Retain deprecated batching max delay in config models for compatibility.
    protected OnnxEmbedder(String className, String bundle, Element xml, DeployState state) {
        super(className, bundle, xml);
        this.forceDisableOptimization = state.featureFlags().forceDisableOnnxModelOptimization();
        var opts = OnnxModelOptions.empty();

        opts = getChildValue(xml, "onnx-execution-mode")
                .map(opts::withExecutionMode)
                .orElse(opts);

        opts = getChildValue(xml, "onnx-interop-threads")
                .map(Integer::parseInt)
                .map(opts::withInterOpThreads)
                .orElse(opts);

        opts = getChildValue(xml, "onnx-intraop-threads")
                .map(Integer::parseInt)
                .map(opts::withIntraOpThreads)
                .orElse(opts);

        opts = getChildValue(xml, "onnx-gpu-device")
                .map(Integer::parseInt)
                .map(OnnxModelOptions.GpuDevice::new)
                .map(opts::withGpuDevice)
                .orElse(opts);

        opts = getChildValue(xml, "onnx-optimize-model")
                .map(Boolean::parseBoolean)
                .map(opts::withOptimizeModel)
                .orElse(opts);

        var batchingConfig = EmbedderBatchingConfig.parseBatchingElement(xml);
        if (batchingConfig != null) {
            opts = opts.withBatchingMaxSize(batchingConfig.maxSize());
            opts = opts.withBatchingMaxDelay(batchingConfig.maxDelay());
        }

        var concurrencyElement = getChild(xml, "concurrency");
        if (concurrencyElement != null) {
            opts = XML.attribute("type", concurrencyElement)
                    .map(opts::withConcurrencyFactorType)
                    .orElse(opts);

            opts = Optional.ofNullable(concurrencyElement.getTextContent())
                    .filter(content -> !content.isBlank())
                    .map(Double::parseDouble)
                    .map(opts::withConcurrencyFactor)
                    .orElse(opts);
        }

        opts = getChildValue(xml, "model-config-override")
                .filter(value -> !value.isBlank())
                .map(value -> state.getFileRegistry().addFile(value))
                .map(opts::withModelConfigOverride)
                .orElse(opts);
        
        onnxModelOptions = opts;
    }

    /**
     * Reads the pooling-strategy element as one of the values the embedder's config supports.
     * The schema allows the union of all embedders' values, so unsupported ones are rejected here.
     */
    protected static <E extends Enum<E>> Optional<E> parsePoolingStrategy(Element xml, Class<E> supported) {
        return getChildValue(xml, "pooling-strategy").map(value -> {
            try {
                return Enum.valueOf(supported, value);
            } catch (IllegalArgumentException e) {
                var supportedNames = Arrays.stream(supported.getEnumConstants()).map(Enum::name).toList();
                throw new IllegalArgumentException("Unsupported pooling-strategy '" + value + "' for " +
                                                   xml.getAttribute("type") + ", supported values are " + supportedNames);
            }
        });
    }

    @Override
    @SuppressWarnings("removal") // Retain deprecated batching max delay in generated config for compatibility.
    public void getConfig(OnnxEvaluatorConfig.Builder builder) {
        onnxModelOptions
                .executionMode()
                .ifPresent(value -> builder.executionMode(
                        OnnxEvaluatorConfig.ExecutionMode.Enum.valueOf(value)));
        onnxModelOptions.interOpThreads().ifPresent(builder::interOpThreads);
        onnxModelOptions.intraOpThreads().ifPresent(builder::intraOpThreads);
        onnxModelOptions.gpuDevice().ifPresent(value -> builder.gpuDevice(value.deviceNumber()));
        onnxModelOptions.batchingMaxSize().ifPresent(builder.batching::maxSize);
        onnxModelOptions.batchingMaxDelay().ifPresent(delay -> builder.batching.maxDelayMillis(delay.toMillis()));
        onnxModelOptions
                .concurrencyFactorType()
                .ifPresent(value ->
                        builder.concurrency.factorType(OnnxEvaluatorConfig.Concurrency.FactorType.Enum.valueOf(value)));
        onnxModelOptions.concurrencyFactor().ifPresent(builder.concurrency::factor);
        builder.modelConfigOverride(onnxModelOptions.modelConfigOverride());
        if (forceDisableOptimization)
            builder.optimizeModel(false);
        else
            onnxModelOptions.optimizeModel().ifPresent(builder::optimizeModel);
    }


}
