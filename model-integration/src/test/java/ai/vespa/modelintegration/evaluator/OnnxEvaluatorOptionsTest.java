// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.evaluator;

import com.yahoo.config.FileReference;
import ai.vespa.modelintegration.evaluator.config.OnnxEvaluatorConfig;
import org.junit.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertTrue(options.batchingMaxDelay().isEmpty());
        assertEquals(1, options.numModelInstances());
        assertTrue(options.modelConfigOverride().isEmpty());
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
        assertTrue(options.batchingMaxDelay().isPresent());
        assertEquals(50, options.batchingMaxDelay().get().toMillis());
        assertEquals(3, options.numModelInstances());
        assertTrue(options.modelConfigOverride().isPresent());
        assertEquals(
                "/path/to/config.pbtxt", options.modelConfigOverride().get().toString());
    }
}
