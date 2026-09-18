// Copyright Vespa.ai. Licensed under the Apache License, Version 2.0.
package ai.vespa.triton;

import ai.vespa.modelintegration.evaluator.OnnxEvaluatorOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static ai.vespa.triton.TritonOnnxRuntime.intraOpThreadsForSeparateSessions;
import static ai.vespa.triton.TritonOnnxRuntime.resolveOptions;
import static inference.ModelConfigOuterClass.ModelInstanceGroup.Kind.KIND_CPU;
import static inference.ModelConfigOuterClass.ModelInstanceGroup.Kind.KIND_GPU;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the defaults TritonOnnxRuntime derives from options. Does not need a Triton server.
 *
 * @author glebashnik
 */
class TritonOnnxRuntimeDefaultsTest {

    private static final String MODEL_PATH = "src/test/models/onnx/transformer/dummy_transformer.onnx";

    private static final boolean SHARED_SESSION = true;
    private static final boolean SEPARATE_SESSIONS = false;

    private static final Set<Integer> NO_GPUS = Set.of();
    private static final Set<Integer> GPUS = Set.of(0);

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
        assertEquals(1, resolveOptions(cpu(16).build(), SEPARATE_SESSIONS, NO_GPUS).numModelInstances().orElseThrow());
        assertEquals(1, resolveOptions(cpu(16).build(), SEPARATE_SESSIONS, GPUS).numModelInstances().orElseThrow());
        assertEquals(1, resolveOptions(requiredGpu(16).build(), SEPARATE_SESSIONS, GPUS).numModelInstances().orElseThrow());
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
        assertEquals(16, resolveOptions(sequential, SHARED_SESSION, NO_GPUS).numModelInstances().orElseThrow());

        var parallel = cpu(16).setExecutionMode(OnnxEvaluatorOptions.ExecutionMode.PARALLEL).build();
        assertEquals(16, resolveOptions(parallel, SHARED_SESSION, NO_GPUS).numModelInstances().orElseThrow());

        var manyThreads = cpu(16).setThreadsFromFactors(1, 32).build();
        assertEquals(16, resolveOptions(manyThreads, SHARED_SESSION, NO_GPUS).numModelInstances().orElseThrow());

        assertEquals(1, resolveOptions(cpu(1).build(), SHARED_SESSION, NO_GPUS).numModelInstances().orElseThrow());
        assertEquals(1, resolveOptions(cpu(0).build(), SHARED_SESSION, NO_GPUS).numModelInstances().orElseThrow());
    }

    @Test
    void shared_session_gets_two_gpu_instances_regardless_of_cpu_cores() {
        assertEquals(2, resolveOptions(cpu(64).build(), SHARED_SESSION, GPUS).numModelInstances().orElseThrow());
        assertEquals(2, resolveOptions(requiredGpu(1).build(), SHARED_SESSION, GPUS).numModelInstances().orElseThrow());
    }

    @Test
    void uses_a_probed_gpu_without_a_gpu_request() {
        var options = resolveOptions(cpu(16).build(), SHARED_SESSION, Set.of(2, 4));
        assertEquals(2, options.gpuDeviceNumber());
        assertTrue(options.requestingGpu());
        assertEquals(2, options.numModelInstances().orElseThrow());
    }

    @Test
    void optional_gpu_requests_fall_back_to_cpu_when_the_probe_finds_no_gpu() {
        for (var options : List.of(cpu(16).build(), requestedGpu(16).build())) {
            var onCpu = resolveOptions(options, SHARED_SESSION, NO_GPUS);
            assertFalse(onCpu.requestingGpu());
            assertEquals(-1, onCpu.gpuDeviceNumber());
            assertEquals(16, onCpu.numModelInstances().orElseThrow());
            assertTrue(resolveOptions(options, SHARED_SESSION, GPUS).requestingGpu());
        }
    }

    @Test
    void required_gpu_requests_fail_when_the_probe_finds_no_gpu() {
        var automatic = requiredGpu(16).build();
        for (var options : List.of(automatic, automatic.withNumModelInstances(3))) {
            for (boolean shareSession : List.of(SEPARATE_SESSIONS, SHARED_SESSION)) {
                var failure = assertThrows(TritonOnnxClient.TritonException.class,
                                           () -> resolveOptions(options, shareSession, NO_GPUS));
                assertEquals("Requested GPU 0 is required, but Triton reported no GPU devices", failure.getMessage());
                var resolved = resolveOptions(options, shareSession, GPUS);
                assertTrue(resolved.requestingGpu());
                assertEquals(0, resolved.gpuDeviceNumber());
            }
        }
    }

    @Test
    void required_flag_without_a_gpu_device_does_not_require_a_gpu() {
        var options = cpu(16).setGpuDevice(-1, true).build();
        assertFalse(resolveOptions(options, SHARED_SESSION, NO_GPUS).requestingGpu());
    }

    @Test
    void explicit_gpu_selects_among_probed_devices() {
        var options = cpu(8).setGpuDevice(4).build();
        assertEquals(4, resolveOptions(options, SHARED_SESSION, Set.of(2, 4)).gpuDeviceNumber());
        assertThrows(TritonOnnxClient.TritonException.class,
                     () -> resolveOptions(options, SHARED_SESSION, Set.of(2)));
    }

    @Test
    void explicit_instance_count_is_preserved_after_device_resolution() {
        var options = cpu(8).build().withNumModelInstances(3);
        assertEquals(3, resolveOptions(options, SHARED_SESSION, GPUS).numModelInstances().orElseThrow());
        assertEquals(3, resolveOptions(options, SHARED_SESSION, NO_GPUS).numModelInstances().orElseThrow());
    }

    private static String modelName(OnnxEvaluatorOptions options, boolean shareSession, Set<Integer> gpuDevices) {
        var resolvedOptions = resolveOptions(options, shareSession, gpuDevices);
        return TritonOnnxRuntime.generateModelName(MODEL_PATH, resolvedOptions, shareSession);
    }

    @Test
    void model_name_is_stable(@TempDir Path directory) throws IOException {
        // Naming only hashes the file contents, so a valid ONNX model is unnecessary.
        var model = Files.writeString(directory.resolve("model.onnx"), "model contents");
        var options = requestedGpu(8).build().withNumModelInstances(2);

        // A fixed expected name detects changes in hashing or encoding across runs.
        assertEquals("model_98ad6a1dc766eee5",
                     TritonOnnxRuntime.generateModelName(model.toString(), options, SHARED_SESSION));
    }

    @Test
    void model_name_accepts_unresolved_instance_counts_without_applying_defaults() {
        for (var options : List.of(cpu(8).build(), requestedGpu(8).build())) {
            for (boolean shareSession : List.of(SEPARATE_SESSIONS, SHARED_SESSION)) {
                var name = assertDoesNotThrow(() ->
                        TritonOnnxRuntime.generateModelName(MODEL_PATH, options, shareSession));
                assertEquals(name, TritonOnnxRuntime.generateModelName(MODEL_PATH, options, shareSession));
                var defaultCount = TritonOnnxRuntime.defaultNumModelInstances(options, shareSession);
                assertNotEquals(name, TritonOnnxRuntime.generateModelName(
                        MODEL_PATH, options.withNumModelInstances(defaultCount), shareSession));
            }
        }
    }

    @Test
    void model_name_depends_on_file_contents(@TempDir Path directory) throws IOException {
        var model = Files.writeString(directory.resolve("model.onnx"), "model contents");
        var options = cpu(8).build().withNumModelInstances(1);
        var originalName = TritonOnnxRuntime.generateModelName(model.toString(), options,
                                                               SEPARATE_SESSIONS);

        Files.writeString(model, "updated model contents");

        assertNotEquals(originalName,
                        TritonOnnxRuntime.generateModelName(model.toString(), options,
                                                            SEPARATE_SESSIONS));
    }

    @Test
    void model_name_depends_on_session_sharing_with_the_same_instance_count() {
        var options = cpu(8).build().withNumModelInstances(1);

        assertNotEquals(modelName(options, SEPARATE_SESSIONS, NO_GPUS),
                        modelName(options, SHARED_SESSION, NO_GPUS));
    }

    @Test
    void model_name_depends_on_thread_count() {
        var options = cpu(8).build().withNumModelInstances(1);
        var moreThreads = new OnnxEvaluatorOptions.Builder(options).setIntraOpThreads(4).build();

        assertNotEquals(modelName(options, SHARED_SESSION, NO_GPUS),
                        modelName(moreThreads, SHARED_SESSION, NO_GPUS));
    }

    // The model name identifies a loaded model, so it must change with the instance group.
    @Test
    void model_name_depends_on_the_resolved_device() {
        var options = cpu(8).build();
        assertNotEquals(modelName(options, SEPARATE_SESSIONS, NO_GPUS),
                        modelName(options, SEPARATE_SESSIONS, GPUS));
        assertNotEquals(modelName(options, SEPARATE_SESSIONS, Set.of(0)),
                        modelName(options, SEPARATE_SESSIONS, Set.of(2)));
        // Explicit and automatic selection of the same device have the same identity.
        assertEquals(modelName(options, SEPARATE_SESSIONS, GPUS),
                     modelName(requiredGpu(8).build(), SEPARATE_SESSIONS, GPUS));
    }

    @Test
    void model_name_uses_the_effective_instance_count() {
        var automatic = cpu(8).build();
        var explicitOne = cpu(8)
                .setConcurrency(1, OnnxEvaluatorOptions.ConcurrencyFactorType.ABSOLUTE)
                .build();

        assertEquals(modelName(automatic, SEPARATE_SESSIONS, NO_GPUS),
                     modelName(explicitOne, SEPARATE_SESSIONS, NO_GPUS));
        assertNotEquals(modelName(automatic, SHARED_SESSION, NO_GPUS),
                        modelName(explicitOne, SHARED_SESSION, NO_GPUS));
    }

    @Test
    void instance_group_uses_only_resolved_cpu_or_gpu_placement() {
        var requested = cpu(16).setGpuDevice(2).build();
        var onGpuNode = TritonOnnxRuntime.createInstanceGroup(resolveOptions(requested, true, Set.of(2)), 2);
        assertEquals(KIND_GPU, onGpuNode.getKind());
        assertEquals(2, onGpuNode.getCount());
        assertEquals(List.of(2), onGpuNode.getGpusList());

        var cpuGroup = TritonOnnxRuntime.createInstanceGroup(resolveOptions(requested, false, NO_GPUS), 1);
        assertEquals(KIND_CPU, cpuGroup.getKind());
        assertEquals(List.of(), cpuGroup.getGpusList());
    }
}
