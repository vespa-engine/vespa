// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.text.XML;
import org.w3c.dom.Element;

import java.time.Duration;

/**
 * Shared parsing of the {@code <batching>} element for embedder components.
 *
 * @author bjorncs
 */
record EmbedderBatchingConfig(int maxSize, Duration maxDelay) {

    static EmbedderBatchingConfig parseBatchingElement(Element parent) {
        var batchingElement = XML.getChild(parent, "batching");
        if (batchingElement == null) return null;
        var maxSize = XML.attribute("max-size", batchingElement)
                .map(value -> {
                    try {
                        return Integer.parseUnsignedInt(value);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(
                                "Batching max-size should be a positive integer, provided: " + value, e);
                    }
                })
                .orElseThrow(() -> new IllegalArgumentException("Missing required attribute 'max-size' on <batching>"));
        var maxDelay = XML.attribute("max-delay", batchingElement)
                .map(v -> Duration.ofMillis(new com.yahoo.vespa.model.utils.Duration(v).getMilliSeconds()))
                .orElseThrow(() -> new IllegalArgumentException("Missing required attribute 'max-delay' on <batching>"));
        return new EmbedderBatchingConfig(maxSize, maxDelay);
    }
}
