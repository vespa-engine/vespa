// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.config.model.api.OnnxModelOptions;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.onnx.OnnxEvaluatorConfig;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.utils.Duration;
import org.w3c.dom.Element;

import java.util.Optional;

import static com.yahoo.text.XML.getChild;
import static com.yahoo.text.XML.getChildValue;

abstract class OnnxEmbedder extends TypedComponent implements OnnxEvaluatorConfig.Producer {
    final protected OnnxModelOptions onnxModelOptions;

    protected OnnxEmbedder(String className, String bundle, Element xml, DeployState state) {
        super(className, bundle, xml);
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

        var batchingElement = getChild(xml, "batching");
        if (batchingElement != null) {
            opts = XML.attribute("max-size", batchingElement)
                    .map(value -> {
                        try {
                            return Integer.parseUnsignedInt(value);
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException(
                                    "Batching max-size should be a positive integer, provided: " + value, e);
                        }
                    })
                    .map(opts::withBatchingMaxSize)
                    .orElse(opts);

            opts = XML.attribute("max-delay", batchingElement)
                    .map(OnnxEmbedder::parseMillis)
                    .map(opts::withBatchingMaxDelay)
                    .orElse(opts);
        }

        var concurrencyElement = getChild(xml, "concurrency");
        if (concurrencyElement != null) {
            opts = XML.attribute("type", concurrencyElement)
                    .map(opts::withConcurrencyType)
                    .orElse(opts);

            opts = Optional.ofNullable(concurrencyElement.getTextContent())
                    .filter(content -> !content.isBlank())
                    .map(Double::parseDouble)
                    .map(opts::withConcurrencyFactorType)
                    .orElse(opts);
        }

        opts = getChildValue(xml, "model-config-override")
                .filter(value -> !value.isBlank())
                .map(value -> state.getFileRegistry().addFile(value))
                .map(opts::withModelConfigOverride)
                .orElse(opts);
        
        onnxModelOptions = opts;
    }

    @Override
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
    }


    private static java.time.Duration parseMillis(String duration) {
        return java.time.Duration.ofMillis(new Duration(duration).getMilliSeconds());
    }
}
