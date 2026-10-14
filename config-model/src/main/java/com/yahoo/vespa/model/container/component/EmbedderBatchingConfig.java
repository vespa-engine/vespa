// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.text.XML;
import org.w3c.dom.Element;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

/**
 * Shared parsing of the {@code <batching>} element for embedder components.
 *
 * @param maxSize        the most requests combined into one inference call
 * @param maxDelay       deprecated and ignored
 * @param paddingBuckets the lengths inputs are padded up to so that different lengths batch, in configured order
 *
 * @author bjorncs
 * @author glebashnik
 * @author ingridhabrekke
 */
record EmbedderBatchingConfig(int maxSize, Duration maxDelay, List<Integer> paddingBuckets) {

    void applyTo(IntConsumer maxSizeSetter, LongConsumer maxDelayMillisSetter) {
        maxSizeSetter.accept(maxSize);
        maxDelayMillisSetter.accept(maxDelay.toMillis());
    }

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
        var paddingBuckets = XML.attribute("padding-buckets", batchingElement)
                .map(EmbedderBatchingConfig::parsePaddingBuckets)
                .orElse(List.of());
        return new EmbedderBatchingConfig(maxSize, maxDelay, paddingBuckets);
    }

    /** Parses a whitespace separated list of positive integers */
    static List<Integer> parsePaddingBuckets(String value) {
        List<Integer> buckets;
        try {
            buckets = Arrays.stream(value.trim().split("\\s+"))
                    .filter(s -> ! s.isEmpty())
                    .map(Integer::parseInt)
                    .toList();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Batching padding-buckets should be a whitespace separated list of " +
                                               "positive integers, provided: '" + value + "'", e);
        }
        if (buckets.stream().anyMatch(bucket -> bucket < 1))
            throw new IllegalArgumentException("Batching padding-buckets should be positive, provided: '" + value + "'");
        return buckets;
    }

}
