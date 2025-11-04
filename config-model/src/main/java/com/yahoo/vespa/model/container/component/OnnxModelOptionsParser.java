// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.config.model.api.OnnxModelOptions;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.utils.Duration;
import org.w3c.dom.Element;

import java.util.Optional;

import static com.yahoo.text.XML.getChild;
import static com.yahoo.text.XML.getChildValue;

/**
 * Parser for ONNX model options from XML elements.
 * Provides reusable parsing logic for all components that use ONNX models.
 *
 * @author glebashnik
 */
public class OnnxModelOptionsParser {
    public static OnnxModelOptions fromXml(Element xml, DeployState state) {
        var options = OnnxModelOptions.empty();

        // Parse execution mode and threading options
        options = getChildValue(xml, "onnx-execution-mode")
                .map(options::withExecutionMode)
                .orElse(options);

        options = getChildValue(xml, "onnx-interop-threads")
                .map(Integer::parseInt)
                .map(options::withInterOpThreads)
                .orElse(options);

        options = getChildValue(xml, "onnx-intraop-threads")
                .map(Integer::parseInt)
                .map(options::withIntraOpThreads)
                .orElse(options);

        options = getChildValue(xml, "onnx-gpu-device")
                .map(Integer::parseInt)
                .map(OnnxModelOptions.GpuDevice::new)
                .map(options::withGpuDevice)
                .orElse(options);

        var batchingElement = getChild(xml, "batching");
        if (batchingElement != null) {
            options = XML.attribute("max-size", batchingElement)
                    .map(value -> {
                        try {
                            return Integer.parseUnsignedInt(value);
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException(
                                    "Batching max-size should be a positive integer, provided: " + value, e);
                        }
                    })
                    .map(options::withBatchingMaxSize)
                    .orElse(options);

            options = XML.attribute("max-delay", batchingElement)
                    .map(OnnxModelOptionsParser::parseMillis)
                    .map(options::withBatchingMaxDelayMillis)
                    .orElse(options);
        }

        var concurrencyElement = getChild(xml, "concurrency");
        if (concurrencyElement != null) {
            options = XML.attribute("type", concurrencyElement)
                    .map(options::withConcurrencyType)
                    .orElse(options);

            options = Optional.ofNullable(concurrencyElement.getTextContent())
                    .filter(content -> !content.isBlank())
                    .map(Double::parseDouble)
                    .map(options::withConcurrencyFactorType)
                    .orElse(options);
        }

        options = getChildValue(xml, "model-config-override")
                .filter(value -> !value.isBlank())
                .map(value -> state.getFileRegistry().addFile(value))
                .map(options::withModelConfigOverride)
                .orElse(options);

        return options;
    }
    
    private static int parseMillis(String duration) {
        return Math.toIntExact(new Duration(duration).getMilliSeconds());
    }
}
