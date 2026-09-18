// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import ai.vespa.modelintegration.evaluator.EmbeddedOnnxRuntime;
import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.yahoo.tensor.Tensor;
import inference.ModelConfigOuterClass.ModelConfig;
import inference.ModelConfigOuterClass.ModelInstanceGroup;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static inference.ModelConfigOuterClass.ModelInstanceGroup.Kind.KIND_AUTO;
import static inference.ModelConfigOuterClass.ModelInstanceGroup.Kind.KIND_CPU;
import static inference.ModelConfigOuterClass.ModelInstanceGroup.Kind.KIND_GPU;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TritonGpuProbeTest {
    private final TritonOnnxClient client = mock(TritonOnnxClient.class);
    private final TritonGpuProbe probe = new TritonGpuProbe(client);

    static ModelConfig config(ModelInstanceGroup.Kind kind, Integer... devices) {
        return ModelConfig.newBuilder().addInstanceGroup(ModelInstanceGroup.newBuilder()
                .setKind(kind).setCount(1).addAllGpus(List.of(devices))).build();
    }

    @Test
    void detects_all_gpu_devices_and_unloads_before_caching() throws Exception {
        when(client.getModelConfig(anyString())).thenReturn(config(KIND_GPU, 0, 2));

        assertEquals(Set.of(0, 2), probe.gpuDevices());
        assertEquals(Set.of(0, 2), probe.gpuDevices());

        var name = ArgumentCaptor.forClass(String.class);
        var json = ArgumentCaptor.forClass(String.class);
        var model = ArgumentCaptor.forClass(ByteString.class);
        var order = inOrder(client);
        order.verify(client).loadModel(name.capture(), json.capture(), model.capture());
        order.verify(client).getModelConfig(name.getValue());
        order.verify(client).unloadModel(name.getValue());
        order.verify(client).unloadUntilModelNotReady(name.getValue());
        order.verifyNoMoreInteractions();

        assertTrue(name.getValue().startsWith("vespa_device_probe_"));
        assertEquals(TritonGpuProbe.createModel(), model.getValue());
        var config = new ObjectMapper().readTree(json.getValue());
        assertEquals(name.getValue(), config.get("name").asText());
        assertEquals(0, config.get("max_batch_size").asInt());
        assertEquals("KIND_AUTO", config.get("instance_group").get(0).get("kind").asText());
        assertEquals(1, config.get("instance_group").get(0).get("count").asInt());
        assertFalse(config.get("instance_group").get(0).has("gpus"));
        assertEquals("input", config.get("input").get(0).get("name").asText());
        assertEquals("output", config.get("output").get(0).get("name").asText());
    }

    @Test
    void caches_a_successful_cpu_result() {
        when(client.getModelConfig(anyString())).thenReturn(config(KIND_CPU));

        assertEquals(Set.of(), probe.gpuDevices());
        assertEquals(Set.of(), probe.gpuDevices());
        verify(client, times(1)).loadModel(anyString(), anyString(), any(ByteString.class));
        verify(client).unloadModel(anyString());
    }

    @Test
    void concurrent_callers_share_one_probe() throws Exception {
        when(client.getModelConfig(anyString())).thenReturn(config(KIND_GPU, 1));
        var executor = Executors.newFixedThreadPool(8);
        var start = new CountDownLatch(1);
        try {
            var results = new ArrayList<Future<Set<Integer>>>();
            for (int i = 0; i < 8; i++) {
                results.add(executor.submit(() -> {
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return probe.gpuDevices();
                }));
            }
            start.countDown();
            for (var result : results) assertEquals(Set.of(1), result.get(10, TimeUnit.SECONDS));
            verify(client, times(1)).loadModel(anyString(), anyString(), any(ByteString.class));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void failed_load_is_cleaned_up_and_retried_with_a_new_name() {
        var failure = new TritonOnnxClient.TritonException("load failed");
        doThrow(failure).doNothing().when(client).loadModel(anyString(), anyString(), any(ByteString.class));
        when(client.getModelConfig(anyString())).thenReturn(config(KIND_GPU, 0));

        assertSame(failure, assertThrows(TritonOnnxClient.TritonException.class, probe::gpuDevices));
        verify(client, never()).getModelConfig(anyString());
        verify(client).unloadModel(anyString());
        assertEquals(Set.of(0), probe.gpuDevices());

        var names = ArgumentCaptor.forClass(String.class);
        verify(client, times(2)).loadModel(names.capture(), anyString(), any(ByteString.class));
        assertNotEquals(names.getAllValues().get(0), names.getAllValues().get(1));
        for (var name : names.getAllValues()) verify(client).unloadModel(name);
    }

    @Test
    void config_failure_is_not_cached_and_cleanup_does_not_hide_it() {
        var failure = new TritonOnnxClient.TritonException("config unavailable");
        var cleanupFailure = new TritonOnnxClient.TritonException("unload unavailable");
        when(client.getModelConfig(anyString())).thenThrow(failure).thenReturn(config(KIND_CPU));
        doThrow(cleanupFailure).doNothing().when(client).unloadModel(anyString());

        assertSame(failure, assertThrows(TritonOnnxClient.TritonException.class, probe::gpuDevices));
        assertArrayEquals(new Throwable[]{cleanupFailure}, failure.getSuppressed());
        assertEquals(Set.of(), probe.gpuDevices());
        verify(client, times(2)).loadModel(anyString(), anyString(), any(ByteString.class));
    }

    @Test
    void cleanup_failure_prevents_caching_a_successful_probe() {
        when(client.getModelConfig(anyString())).thenReturn(config(KIND_CPU));
        doThrow(new TritonOnnxClient.TritonException("still unloading")).doNothing()
                .when(client).unloadUntilModelNotReady(anyString());

        assertThrows(TritonOnnxClient.TritonException.class, probe::gpuDevices);
        assertEquals(Set.of(), probe.gpuDevices());
        verify(client, times(2)).loadModel(anyString(), anyString(), any(ByteString.class));
    }

    @Test
    void malformed_or_unresolved_configs_are_not_interpreted_as_no_gpu() {
        for (var config : List.of(ModelConfig.getDefaultInstance(), config(KIND_AUTO), config(KIND_GPU),
                                   config(KIND_GPU, -1), config(KIND_CPU, 0))) {
            when(client.getModelConfig(anyString())).thenReturn(config);
            assertThrows(TritonOnnxClient.TritonException.class, probe::gpuDevices);
        }
        verify(client, times(5)).unloadModel(anyString());
    }

    @Test
    void generated_model_runs_in_onnx_runtime() {
        assumeTrue(OnnxRuntime.isRuntimeAvailable());
        var runtime = EmbeddedOnnxRuntime.createTestInstance();
        try (var evaluator = runtime.evaluatorOf(TritonGpuProbe.createModel().toByteArray())) {
            var input = Tensor.from("tensor<float>(d0[1]):[3]");
            assertEquals(Tensor.from("tensor<float>(d0[1]):[6]"),
                         evaluator.evaluate(Map.of("input", input), "output"));
        } finally {
            runtime.deconstruct();
        }
    }
}
