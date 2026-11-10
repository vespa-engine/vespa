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
    public void builder_of_config_with_defaults() {
        var configBuilder = new OnnxEvaluatorConfig.Builder();
        configBuilder.executionMode(OnnxEvaluatorConfig.ExecutionMode.Enum.parallel);
        configBuilder.gpuDevice(2);
        var config = configBuilder.build();

        var options = OnnxEvaluatorOptions.builder(config, 8).build();

        assertEquals(OnnxEvaluatorOptions.ExecutionMode.PARALLEL, options.executionMode());
        assertEquals(1, options.interOpThreads());
        assertEquals(2, options.intraOpThreads());
        assertEquals(2, options.gpuDeviceNumber());
        assertEquals(1, options.batchingMaxSize());
        assertTrue(options.numModelInstances().isEmpty());
        assertTrue(options.modelConfigOverride().isEmpty());
    }

    @Test
    public void first_dimension_is_not_a_batch_unless_set() {
        assertFalse(OnnxEvaluatorOptions.createDefault().firstDimensionIsBatch());
        assertFalse(new OnnxEvaluatorOptions.Builder(16).build().firstDimensionIsBatch());
        assertFalse(OnnxEvaluatorOptions.builder(new OnnxEvaluatorConfig.Builder().build(), 8).build().firstDimensionIsBatch());
    }

    @Test
    public void padding_is_unset_unless_padding_buckets_are_configured() {
        var batchingOnly = new OnnxEvaluatorConfig.Builder();
        batchingOnly.batching.maxSize(8);
        var options = OnnxEvaluatorOptions.builder(batchingOnly.build(), 8).build();
        assertTrue(options.padding().isEmpty());
        assertEquals(options, OnnxEvaluatorOptions.builder(batchingOnly.build(), 8)
                                                  .setPaddingAttentionMask("mask").setPaddingMaxTokens(32).build(),
                     "Nothing to refine without padding buckets");

        var withBuckets = new OnnxEvaluatorConfig.Builder();
        withBuckets.batching.maxSize(8).paddingBuckets(List.of(16, 64));
        var refined = OnnxEvaluatorOptions.builder(withBuckets.build(), 8)
                                          .setFirstDimensionIsBatch(true)
                                          .setPaddingAttentionMask("mask")
                                          .setPaddingMaxTokens(32)
                                          .build()
                                          .padding().orElseThrow();
        assertEquals("mask", refined.attentionMaskName());
        assertEquals(List.of(16, 32), refined.buckets());
    }

    @Test
    public void padding_refinements_apply_regardless_of_setter_order() {
        var refined = new OnnxEvaluatorOptions.Builder(16)
                .setFirstDimensionIsBatch(true)
                .setPaddingAttentionMask("mask")
                .setPaddingMaxTokens(32)
                .setPadding(Optional.of(OnnxPadderOptions.of(List.of(16, 64))))
                .build()
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

        var copied = absolute.toBuilder().build();
        assertEquals(Optional.of(5), copied.numModelInstances());
        assertEquals(16, copied.availableProcessors());
        assertEquals(absolute, copied);
    }

    @Test
    public void padding_and_runtime_pooling_need_first_dimension_is_batch() {
        var builder = new OnnxEvaluatorOptions.Builder(16).setFirstDimensionIsBatch(false);
        assertThrows(IllegalArgumentException.class,
                     () -> builder.setPadding(Optional.of(OnnxPadderOptions.of(List.of(8, 16)))).build());
        builder.setPadding(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                     () -> builder.setRuntimePooling(Optional.of(new OnnxPoolingOptions(OnnxPoolingOptions.Strategy.MEAN, "output", "attention_mask"))).build());
    }

    @Test
    public void optional_components_cannot_be_null() {
        var options = OnnxEvaluatorOptions.createDefault();
        assertThrows(NullPointerException.class, () -> new OnnxEvaluatorOptions(
                options.executionMode(), options.interOpThreads(), options.intraOpThreads(),
                options.gpuDeviceNumber(), options.gpuDeviceRequired(), options.optimizeModel(),
                options.batchingMaxSize(), options.firstDimensionIsBatch(), options.numModelInstances(), options.modelConfigOverride(), options.padding(), null, options.availableProcessors()));
        assertThrows(NullPointerException.class, () -> new OnnxEvaluatorOptions(
                options.executionMode(), options.interOpThreads(), options.intraOpThreads(),
                options.gpuDeviceNumber(), options.gpuDeviceRequired(), options.optimizeModel(),
                options.batchingMaxSize(), options.firstDimensionIsBatch(), null, options.modelConfigOverride(), options.padding(), options.runtimePooling(), options.availableProcessors()));
        assertThrows(NullPointerException.class, () -> new OnnxEvaluatorOptions(
                options.executionMode(), options.interOpThreads(), options.intraOpThreads(),
                options.gpuDeviceNumber(), options.gpuDeviceRequired(), options.optimizeModel(),
                options.batchingMaxSize(), options.firstDimensionIsBatch(), options.numModelInstances(), null, options.padding(), options.runtimePooling(), options.availableProcessors()));
        assertThrows(NullPointerException.class, () -> new OnnxEvaluatorOptions(
                options.executionMode(), options.interOpThreads(), options.intraOpThreads(),
                options.gpuDeviceNumber(), options.gpuDeviceRequired(), options.optimizeModel(),
                options.batchingMaxSize(), options.firstDimensionIsBatch(), options.numModelInstances(), options.modelConfigOverride(), null, options.runtimePooling(), options.availableProcessors()));
    }

    @Test
    public void explicit_instance_count_must_be_positive() {
        var options = new OnnxEvaluatorOptions.Builder(8).setNumModelInstances(1).build();
        assertEquals(Optional.of(1), options.numModelInstances());

        for (int count : new int[] { 0, -1 }) {
            assertThrows(IllegalArgumentException.class,
                         () -> options.toBuilder().setNumModelInstances(count));
            assertThrows(IllegalArgumentException.class, () -> options.toBuilder().setNumModelInstances(count).build());
        }
    }

    @Test
    public void builder_of_config_with_negative_gpu_device_disables_gpu() {
        var configBuilder = new OnnxEvaluatorConfig.Builder();
        configBuilder.gpuDevice(-1);
        var config = configBuilder.build();

        var options = OnnxEvaluatorOptions.builder(config, 8).build();

        assertEquals(-1, options.gpuDeviceNumber());
        assertFalse(options.requestingGpu());
    }

    @Test
    public void builder_of_config_with_all_params_set() {
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

        var options = OnnxEvaluatorOptions.builder(config).build();

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
        assertEquals(options, OnnxEvaluatorOptions.builder(configWithDifferentDelay.build()).build());
    }
}
