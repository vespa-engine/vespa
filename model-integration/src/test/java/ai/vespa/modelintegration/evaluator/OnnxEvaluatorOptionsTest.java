// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.evaluator;

import com.yahoo.config.FileReference;
import com.yahoo.onnx.OnnxEvaluatorConfig;
import org.junit.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
/**
 * @author glebashnik
 */
public class OnnxEvaluatorOptionsTest {
    @Test
    public void testOfOnnxEvaluatorConfig() {
        var configBuilder = new OnnxEvaluatorConfig.Builder();
        // ONNX evaluator options
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
        assertEquals(50, options.batchingMaxDelay().toMillis());
        assertEquals(3, options.numModelInstances());
        assertTrue(options.modelConfigOverride().isPresent());
        assertEquals(
                "/path/to/config.pbtxt", options.modelConfigOverride().get().toString());
    }
}
