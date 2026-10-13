// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.config.FileReference;
import com.yahoo.config.model.api.OnnxModelOptions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author ingrid
 */
class OnnxEmbedderTest {

    private static final List<Integer> BUCKETS = List.of(16, 64);
    private static final EmbedderBatchingConfig BATCHING = new EmbedderBatchingConfig(8, Duration.ofMillis(1));

    @Test
    void paddingHasNoEffectWithoutBatching() {
        var options = OnnxModelOptions.empty();
        assertEquals(Optional.of("the batching max-size is not above 1"),
                     OnnxEmbedder.reasonPaddingHasNoEffect(BUCKETS, null, options));
        assertEquals(Optional.of("the batching max-size is not above 1"),
                     OnnxEmbedder.reasonPaddingHasNoEffect(BUCKETS, new EmbedderBatchingConfig(1, Duration.ofMillis(1)), options));
    }

    @Test
    void paddingHasNoEffectOnCpu() {
        var options = OnnxModelOptions.empty().withGpuDevice(new OnnxModelOptions.GpuDevice(-1));
        assertEquals(Optional.of("onnx-gpu-device is -1, which means CPU"),
                     OnnxEmbedder.reasonPaddingHasNoEffect(BUCKETS, BATCHING, options));
    }

    @Test
    void paddingMayHaveEffectOnAGpu() {
        var options = OnnxModelOptions.empty().withGpuDevice(new OnnxModelOptions.GpuDevice(0));
        assertEquals(Optional.empty(), OnnxEmbedder.reasonPaddingHasNoEffect(BUCKETS, BATCHING, options));
    }

    @Test
    void nothingIsReportedWithoutBuckets() {
        assertEquals(Optional.empty(), OnnxEmbedder.reasonPaddingHasNoEffect(List.of(), null, OnnxModelOptions.empty()));
    }

}
