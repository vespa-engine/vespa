// Copyright Vespa.ai. Licensed under the Apache License, Version 2.0.
package ai.vespa.triton;

import ai.vespa.modelintegration.evaluator.OnnxEvaluatorOptions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static ai.vespa.triton.TritonOnnxRuntime.defaultNumModelInstances;
import static ai.vespa.triton.TritonOnnxRuntime.deviceKind;
import static ai.vespa.triton.TritonOnnxRuntime.intraOpThreadsForSeparateSessions;
import static inference.ModelConfigOuterClass.ModelInstanceGroup.Kind.KIND_AUTO;
import static inference.ModelConfigOuterClass.ModelInstanceGroup.Kind.KIND_CPU;
import static inference.ModelConfigOuterClass.ModelInstanceGroup.Kind.KIND_GPU;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Tests the defaults TritonOnnxRuntime derives from options. Does not need a Triton server.
 *
 * @author glebashnik
 */
class TritonOnnxRuntimeDefaultsTest {

    private static final String MODEL_PATH = "src/test/models/onnx/transformer/dummy_transformer.onnx";

    private static final boolean SHARED_SESSION = true;
    private static final boolean SEPARATE_SESSIONS = false;

    private static final boolean GPU_UNAVAILABLE = false;
    private static final boolean GPU_AVAILABLE = true;

    // Default threads on 16 CPU cores are 4 intra-op and 4 inter-op.
    private static OnnxEvaluatorOptions.Builder cpu(int cpuCores) {
        return new OnnxEvaluatorOptions.Builder(cpuCores);
    }

    // The GPU is not required, so Triton falls back to CPU without one.
    private static OnnxEvaluatorOptions.Builder requestedGpu(int cpuCores) {
        return new OnnxEvaluatorOptions.Builder(cpuCores).setGpuDevice(0);
    }

    private static OnnxEvaluatorOptions.Builder requiredGpu(int cpuCores) {
        return new OnnxEvaluatorOptions.Builder(cpuCores).setGpuDevice(0, true);
    }

    @Test
    void separate_sessions_get_one_instance_on_cpu_and_gpu() {
        assertEquals(1, defaultNumModelInstances(cpu(16).build(), SEPARATE_SESSIONS, GPU_UNAVAILABLE));
        assertEquals(1, defaultNumModelInstances(requestedGpu(16).build(), SEPARATE_SESSIONS, GPU_AVAILABLE));
        assertEquals(1, defaultNumModelInstances(requiredGpu(16).build(), SEPARATE_SESSIONS, GPU_AVAILABLE));
    }

    @Test
    void separate_sessions_divide_the_cpu_cores_between_instances_as_intra_op_threads() {
        var options = cpu(8).setThreadsFromFactors(1, 16).build(); // Configured threads are not used
        assertEquals(8, intraOpThreadsForSeparateSessions(options, 1));
        assertEquals(4, intraOpThreadsForSeparateSessions(options, 2));
        assertEquals(3, intraOpThreadsForSeparateSessions(options, 3)); // Rounds up
        assertEquals(1, intraOpThreadsForSeparateSessions(options, 12)); // At least one
    }

    @Test
    void shared_session_gets_one_cpu_instance_per_core_regardless_of_threads() {
        var sequential = cpu(16).build();
        assertEquals(16, defaultNumModelInstances(sequential, SHARED_SESSION, GPU_UNAVAILABLE));

        var parallel = cpu(16).setExecutionMode(OnnxEvaluatorOptions.ExecutionMode.PARALLEL).build();
        assertEquals(16, defaultNumModelInstances(parallel, SHARED_SESSION, GPU_UNAVAILABLE));

        var manyThreads = cpu(16).setThreadsFromFactors(1, 32).build();
        assertEquals(16, defaultNumModelInstances(manyThreads, SHARED_SESSION, GPU_UNAVAILABLE));

        assertEquals(1, defaultNumModelInstances(cpu(1).build(), SHARED_SESSION, GPU_UNAVAILABLE));
        assertEquals(1, defaultNumModelInstances(cpu(0).build(), SHARED_SESSION, GPU_UNAVAILABLE));
    }

    @Test
    void shared_session_gets_two_gpu_instances_regardless_of_cpu_cores() {
        assertEquals(2, defaultNumModelInstances(requestedGpu(64).build(), SHARED_SESSION, GPU_AVAILABLE));
        assertEquals(2, defaultNumModelInstances(requiredGpu(1).build(), SHARED_SESSION, GPU_UNAVAILABLE));
    }

    @Test
    void requested_gpu_is_only_used_when_the_node_has_one() {
        var requestedGpu = requestedGpu(16).build();
        assertEquals(2, defaultNumModelInstances(requestedGpu, SHARED_SESSION, GPU_AVAILABLE));
        assertEquals(16, defaultNumModelInstances(requestedGpu, SHARED_SESSION, GPU_UNAVAILABLE));
    }

    @Test
    void device_kind_follows_the_gpu_request_and_the_gpus_on_the_node() {
        var noGpu = cpu(16).build();
        assertEquals(KIND_CPU, deviceKind(noGpu, GPU_UNAVAILABLE));
        assertEquals(KIND_CPU, deviceKind(noGpu, GPU_AVAILABLE));

        // Without a known GPU, Triton tries the GPU and falls back to CPU, like EmbeddedOnnxRuntime tries CUDA.
        var requestedGpu = requestedGpu(16).build();
        assertEquals(KIND_AUTO, deviceKind(requestedGpu, GPU_UNAVAILABLE));
        assertEquals(KIND_GPU, deviceKind(requestedGpu, GPU_AVAILABLE));

        var requiredGpu = requiredGpu(16).build();
        assertEquals(KIND_GPU, deviceKind(requiredGpu, GPU_UNAVAILABLE));
        assertEquals(KIND_GPU, deviceKind(requiredGpu, GPU_AVAILABLE));

        // Like EmbeddedOnnxRuntime, required is ignored without a GPU device
        var requiredWithoutDevice = cpu(16).setGpuDevice(-1, true).build();
        assertEquals(KIND_CPU, deviceKind(requiredWithoutDevice, GPU_AVAILABLE));
        assertEquals(16, defaultNumModelInstances(requiredWithoutDevice, SHARED_SESSION, GPU_AVAILABLE));
    }

    private static String modelName(OnnxEvaluatorOptions options, boolean shareSession, boolean gpuAvailable) {
        var resolvedOptions = TritonOnnxRuntime.resolveOptions(options, shareSession, gpuAvailable);
        return TritonOnnxRuntime.generateModelName(MODEL_PATH, resolvedOptions, shareSession, gpuAvailable);
    }

    // The model name identifies a loaded model, so it must change with the instance group.
    @Test
    void model_name_depends_on_the_resolved_device() {
        var requestedGpu = requestedGpu(8).build();
        assertNotEquals(modelName(requestedGpu, SEPARATE_SESSIONS, GPU_UNAVAILABLE),
                        modelName(requestedGpu, SEPARATE_SESSIONS, GPU_AVAILABLE));

        // A required GPU and a CPU model resolve to the same device either way
        var requiredGpu = requiredGpu(8).build();
        assertEquals(modelName(requiredGpu, SEPARATE_SESSIONS, GPU_UNAVAILABLE),
                     modelName(requiredGpu, SEPARATE_SESSIONS, GPU_AVAILABLE));
        var cpu = cpu(8).build();
        assertEquals(modelName(cpu, SEPARATE_SESSIONS, GPU_UNAVAILABLE),
                     modelName(cpu, SEPARATE_SESSIONS, GPU_AVAILABLE));
    }

    @Test
    void model_name_uses_the_effective_instance_count() {
        var automatic = cpu(8).build();
        var explicitOne = cpu(8)
                .setConcurrency(1, OnnxEvaluatorOptions.ConcurrencyFactorType.ABSOLUTE)
                .build();

        assertEquals(modelName(automatic, SEPARATE_SESSIONS, GPU_UNAVAILABLE),
                     modelName(explicitOne, SEPARATE_SESSIONS, GPU_UNAVAILABLE));
        assertNotEquals(modelName(automatic, SHARED_SESSION, GPU_UNAVAILABLE),
                        modelName(explicitOne, SHARED_SESSION, GPU_UNAVAILABLE));
    }

    @Test
    void gpu_instance_group_pins_the_requested_device_without_breaking_cpu_fallback() {
        var requested = cpu(16).setGpuDevice(2).build();
        var onGpuNode = TritonOnnxRuntime.createInstanceGroup(requested, 2, GPU_AVAILABLE);
        assertEquals(KIND_GPU, onGpuNode.getKind());
        assertEquals(2, onGpuNode.getCount());
        assertEquals(List.of(2), onGpuNode.getGpusList());

        var required = cpu(16).setGpuDevice(3, true).build();
        var requiredGroup = TritonOnnxRuntime.createInstanceGroup(required, 2, GPU_UNAVAILABLE);
        assertEquals(KIND_GPU, requiredGroup.getKind());
        assertEquals(List.of(3), requiredGroup.getGpusList());

        var unknownNode = TritonOnnxRuntime.createInstanceGroup(requested, 1, GPU_UNAVAILABLE);
        assertEquals(KIND_AUTO, unknownNode.getKind());
        assertEquals(List.of(), unknownNode.getGpusList());

        var cpuGroup = TritonOnnxRuntime.createInstanceGroup(cpu(16).build(), 1, GPU_AVAILABLE);
        assertEquals(KIND_CPU, cpuGroup.getKind());
        assertEquals(List.of(), cpuGroup.getGpusList());
    }
}
