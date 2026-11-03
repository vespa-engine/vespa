// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.evaluator;

import com.yahoo.config.FileReference;
import ai.vespa.modelintegration.evaluator.config.OnnxEvaluatorConfig;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
/**
 * @author glebashnik
 */
public class OnnxEvaluatorOptionsTest {
    @Test
    public void of_config_with_defaults() {
        var configBuilder = new OnnxEvaluatorConfig.Builder();
        configBuilder.executionMode(OnnxEvaluatorConfig.ExecutionMode.Enum.parallel);
        configBuilder.gpuDevice(2);
        var config = configBuilder.build();

        var options = OnnxEvaluatorOptions.of(config, 8);

        assertEquals(OnnxEvaluatorOptions.ExecutionMode.PARALLEL, options.executionMode());
        assertEquals(1, options.interOpThreads());
        assertEquals(2, options.intraOpThreads());
        assertEquals(2, options.gpuDeviceNumber());
        assertEquals(1, options.batchingMaxSize());
        assertTrue(options.numModelInstances().isEmpty());
        assertTrue(options.modelConfigOverride().isEmpty());
    }

    @Test
    public void padding_is_unset_unless_padding_buckets_are_configured() {
        var batchingOnly = new OnnxEvaluatorConfig.Builder();
        batchingOnly.batching.maxSize(8);
        var options = OnnxEvaluatorOptions.of(batchingOnly.build(), 8);
        assertTrue(options.padding().isEmpty());
        assertEquals(options, options.withAttentionMask("mask").withMaxTokens(32), "Nothing to refine without padding buckets");

        var withBuckets = new OnnxEvaluatorConfig.Builder();
        withBuckets.batching.maxSize(8).paddingBuckets(List.of(16, 64));
        var refined = OnnxEvaluatorOptions.of(withBuckets.build(), 8)
                                          .withAttentionMask("mask").withMaxTokens(32)
                                          .padding().orElseThrow();
        assertEquals("mask", refined.attentionMaskName());
        assertEquals(List.of(16, 32), refined.buckets());
    }

    @Test
    public void num_model_instances_is_unset_unless_concurrency_is_positive() {
        assertTrue(new OnnxEvaluatorOptions.Builder(16).build().numModelInstances().isEmpty());

        var zeroFactor = new OnnxEvaluatorOptions.Builder(16)
                .setConcurrency(0, OnnxEvaluatorOptions.ConcurrencyFactorType.ABSOLUTE)
                .build();
        assertTrue(zeroFactor.numModelInstances().isEmpty());

        var negativeFactor = new OnnxEvaluatorOptions.Builder(16)
                .setConcurrency(-1, OnnxEvaluatorOptions.ConcurrencyFactorType.RELATIVE)
                .build();
        assertTrue(negativeFactor.numModelInstances().isEmpty());

        var absolute = new OnnxEvaluatorOptions.Builder(16)
                .setConcurrency(5, OnnxEvaluatorOptions.ConcurrencyFactorType.ABSOLUTE)
                .build();
        assertEquals(Optional.of(5), absolute.numModelInstances());

        var relative = new OnnxEvaluatorOptions.Builder(16)
                .setConcurrency(0.5, OnnxEvaluatorOptions.ConcurrencyFactorType.RELATIVE)
                .build();
        assertEquals(Optional.of(8), relative.numModelInstances());

        var copied = new OnnxEvaluatorOptions.Builder(absolute).build();
        assertEquals(Optional.of(5), copied.numModelInstances());
        assertEquals(16, copied.availableProcessors());
        assertEquals(absolute, copied);
    }

    @Test
    public void optional_components_cannot_be_null() {
        var options = OnnxEvaluatorOptions.createDefault();
        assertThrows(NullPointerException.class, () -> new OnnxEvaluatorOptions(
                options.executionMode(), options.interOpThreads(), options.intraOpThreads(),
                options.gpuDeviceNumber(), options.gpuDeviceRequired(), options.optimizeModel(),
                options.batchingMaxSize(), options.numModelInstances(), options.modelConfigOverride(), options.padding(), null, options.availableProcessors()));
        assertThrows(NullPointerException.class, () -> new OnnxEvaluatorOptions(
                options.executionMode(), options.interOpThreads(), options.intraOpThreads(),
                options.gpuDeviceNumber(), options.gpuDeviceRequired(), options.optimizeModel(),
                options.batchingMaxSize(), null, options.modelConfigOverride(), options.padding(), options.runtimePooling(), options.availableProcessors()));
        assertThrows(NullPointerException.class, () -> new OnnxEvaluatorOptions(
                options.executionMode(), options.interOpThreads(), options.intraOpThreads(),
                options.gpuDeviceNumber(), options.gpuDeviceRequired(), options.optimizeModel(),
                options.batchingMaxSize(), options.numModelInstances(), null, options.padding(), options.runtimePooling(), options.availableProcessors()));
        assertThrows(NullPointerException.class, () -> new OnnxEvaluatorOptions(
                options.executionMode(), options.interOpThreads(), options.intraOpThreads(),
                options.gpuDeviceNumber(), options.gpuDeviceRequired(), options.optimizeModel(),
                options.batchingMaxSize(), options.numModelInstances(), options.modelConfigOverride(), null, options.runtimePooling(), options.availableProcessors()));
    }

    @Test
    public void explicit_instance_count_must_be_positive() {
        var options = new OnnxEvaluatorOptions.Builder(8).setNumModelInstances(1).build();
        assertEquals(Optional.of(1), options.numModelInstances());

        for (int count : new int[] { 0, -1 }) {
            assertThrows(IllegalArgumentException.class,
                         () -> new OnnxEvaluatorOptions.Builder(options).setNumModelInstances(count));
            assertThrows(IllegalArgumentException.class, () -> options.withNumModelInstances(count));
        }
    }

    @Test
    public void of_config_with_negative_gpu_device_disables_gpu() {
        var configBuilder = new OnnxEvaluatorConfig.Builder();
        configBuilder.gpuDevice(-1);
        var config = configBuilder.build();

        var options = OnnxEvaluatorOptions.of(config, 8);

        assertEquals(-1, options.gpuDeviceNumber());
        assertFalse(options.requestingGpu());
    }

    @Test
    public void of_config_with_all_params_set() {
        var configBuilder = new OnnxEvaluatorConfig.Builder();
        configBuilder.executionMode(OnnxEvaluatorConfig.ExecutionMode.Enum.parallel);
        configBuilder.interOpThreads(4);
        configBuilder.intraOpThreads(8);
        configBuilder.gpuDevice(2);

        var batchingBuilder = new OnnxEvaluatorConfig.Batching.Builder();
        batchingBuilder.maxSize(10);
        batchingBuilder.maxDelayMillis(50);
        configBuilder.batching(batchingBuilder);

        var concurrencyBuilder = new OnnxEvaluatorConfig.Concurrency.Builder();
        concurrencyBuilder.factor(3.0);
        concurrencyBuilder.factorType(OnnxEvaluatorConfig.Concurrency.FactorType.Enum.absolute);

        configBuilder.concurrency(concurrencyBuilder);
        configBuilder.modelConfigOverride(Optional.of(new FileReference("/path/to/config.pbtxt")));
        var config = configBuilder.build();

        var options = OnnxEvaluatorOptions.of(config);

        assertEquals(OnnxEvaluatorOptions.ExecutionMode.PARALLEL, options.executionMode());
        assertEquals(4, options.interOpThreads());
        assertEquals(8, options.intraOpThreads());
        assertEquals(2, options.gpuDeviceNumber());
        assertEquals(10, options.batchingMaxSize());
        assertEquals(Optional.of(3), options.numModelInstances());
        assertTrue(options.modelConfigOverride().isPresent());
        assertEquals(
                "/path/to/config.pbtxt", options.modelConfigOverride().get().toString());

        var configWithDifferentDelay = new OnnxEvaluatorConfig.Builder(config);
        configWithDifferentDelay.batching.maxDelayMillis(100);
        assertEquals(options, OnnxEvaluatorOptions.of(configWithDifferentDelay.build()));
    }
}
