// Copyright Vespa.ai. Licensed under the Apache License, Version 2.0.
package ai.vespa.triton;

import ai.vespa.llm.clients.TritonConfig;
import ai.vespa.modelintegration.evaluator.OnnxEvaluatorOptions;
import com.google.protobuf.TextFormat;
import inference.ModelConfigOuterClass.ModelConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static ai.vespa.triton.TritonOnnxRuntime.intraOpThreadsForSeparateSessions;
import static ai.vespa.triton.TritonOnnxRuntime.resolveOptions;
import static inference.ModelConfigOuterClass.ModelInstanceGroup.Kind.KIND_AUTO;
import static inference.ModelConfigOuterClass.ModelInstanceGroup.Kind.KIND_CPU;
import static inference.ModelConfigOuterClass.ModelInstanceGroup.Kind.KIND_GPU;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests the defaults TritonOnnxRuntime derives from options. Does not need a Triton server.
 *
 * @author glebashnik
 */
class TritonOnnxRuntimeDefaultsTest {

    private static final String MODEL_PATH = "src/test/models/onnx/transformer/dummy_transformer.onnx";

    private static final boolean SHARED_SESSION = true;
    private static final boolean SEPARATE_SESSIONS = false;

    private static OnnxEvaluatorOptions.Builder options(int cpuCores) {
        return new OnnxEvaluatorOptions.Builder(cpuCores);
    }

    @Test
    void separate_sessions_get_one_instance_regardless_of_device() {
        for (int device : List.of(-1, 0, 2)) {
            var options = options(16).setGpuDevice(device).build();
            assertEquals(1, resolveOptions(options, SEPARATE_SESSIONS).numModelInstances().orElseThrow());
        }
    }

    @Test
    void separate_sessions_divide_the_cpu_cores_between_instances_as_intra_op_threads() {
        var options = options(8).setThreadsFromFactors(1, 16).build(); // Configured threads are not used
        assertEquals(8, intraOpThreadsForSeparateSessions(options, 1));
        assertEquals(4, intraOpThreadsForSeparateSessions(options, 2));
        assertEquals(3, intraOpThreadsForSeparateSessions(options, 3)); // Rounds up
        assertEquals(1, intraOpThreadsForSeparateSessions(options, 12)); // At least one
    }

    @Test
    void shared_session_gets_one_instance_per_core_regardless_of_device_or_threads() {
        for (int device : List.of(-1, 0, 2)) {
            for (var options : List.of(options(16).setGpuDevice(device).build(),
                                       options(16).setGpuDevice(device).setThreadsFromFactors(1, 32).build(),
                                       options(16).setGpuDevice(device)
                                               .setExecutionMode(OnnxEvaluatorOptions.ExecutionMode.PARALLEL).build())) {
                assertEquals(16, resolveOptions(options, SHARED_SESSION).numModelInstances().orElseThrow());
            }
        }
        assertEquals(1, resolveOptions(options(1).build(), SHARED_SESSION).numModelInstances().orElseThrow());
        assertEquals(1, resolveOptions(options(0).build(), SHARED_SESSION).numModelInstances().orElseThrow());
    }

    @Test
    void explicit_instance_count_is_preserved() {
        var options = options(8).build().withNumModelInstances(3);
        assertSame(options, resolveOptions(options, SHARED_SESSION));
        assertSame(options, resolveOptions(options, SEPARATE_SESSIONS));
    }

    private static String modelName(OnnxEvaluatorOptions options, boolean shareSession) {
        var resolvedOptions = resolveOptions(options, shareSession);
        return TritonOnnxRuntime.generateModelName(MODEL_PATH, resolvedOptions, shareSession);
    }

    @Test
    void model_name_is_stable(@TempDir Path directory) throws IOException {
        // Naming only hashes the file contents, so a valid ONNX model is unnecessary.
        var model = Files.writeString(directory.resolve("model.onnx"), "model contents");
        var options = options(8).setGpuDevice(0).build().withNumModelInstances(2);

        // A fixed expected name detects changes in hashing or encoding across runs.
        assertEquals("model_98ad6a1dc766eee5",
                     TritonOnnxRuntime.generateModelName(model.toString(), options, SHARED_SESSION));
    }

    @Test
    void model_name_accepts_unresolved_instance_counts_without_applying_defaults() {
        for (var options : List.of(options(8).build(), options(8).setGpuDevice(0).build())) {
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
        var options = options(8).build().withNumModelInstances(1);
        var originalName = TritonOnnxRuntime.generateModelName(model.toString(), options,
                                                               SEPARATE_SESSIONS);

        Files.writeString(model, "updated model contents");

        assertNotEquals(originalName,
                        TritonOnnxRuntime.generateModelName(model.toString(), options,
                                                            SEPARATE_SESSIONS));
    }

    @Test
    void model_name_depends_on_session_sharing_with_the_same_instance_count() {
        var options = options(8).build().withNumModelInstances(1);

        assertNotEquals(modelName(options, SEPARATE_SESSIONS),
                        modelName(options, SHARED_SESSION));
    }

    @Test
    void model_name_depends_on_thread_count() {
        var options = options(8).build().withNumModelInstances(1);
        var moreThreads = new OnnxEvaluatorOptions.Builder(options).setIntraOpThreads(4).build();

        assertNotEquals(modelName(options, SHARED_SESSION),
                        modelName(moreThreads, SHARED_SESSION));
    }

    // The model name identifies a loaded model, so it must change with the instance group.
    @Test
    void model_name_depends_on_device_options() {
        assertNotEquals(modelName(options(8).build(), SEPARATE_SESSIONS),
                        modelName(options(8).setGpuDevice(0).build(), SEPARATE_SESSIONS));
        assertNotEquals(modelName(options(8).setGpuDevice(0, true).build(), SEPARATE_SESSIONS),
                        modelName(options(8).setGpuDevice(2, true).build(), SEPARATE_SESSIONS));
    }

    @Test
    void model_name_uses_the_effective_instance_count() {
        var automatic = options(8).build();
        var explicitOne = options(8)
                .setConcurrency(1, OnnxEvaluatorOptions.ConcurrencyFactorType.ABSOLUTE)
                .build();

        assertEquals(modelName(automatic, SEPARATE_SESSIONS),
                     modelName(explicitOne, SEPARATE_SESSIONS));
        assertNotEquals(modelName(automatic, SHARED_SESSION),
                        modelName(explicitOne, SHARED_SESSION));
    }

    @Test
    void instance_group_respects_device_options() {
        for (int device : List.of(-1, 0, 2)) {
            for (boolean required : List.of(false, true)) {
                var group = TritonOnnxRuntime.createInstanceGroup(options(8).setGpuDevice(device, required).build(), 8);
                assertEquals(required ? KIND_GPU : device < 0 ? KIND_CPU : KIND_AUTO, group.getKind());
                assertEquals(8, group.getCount());
                assertEquals(required && device >= 0 ? List.of(device) : List.of(), group.getGpusList());
            }
        }
    }

    @Test
    void full_config_override_preserves_instance_configuration_and_unresolved_options(@TempDir Path directory)
            throws IOException {
        var client = mock(TritonOnnxClient.class);
        var runtime = new TritonOnnxRuntime(new TritonConfig.Builder()
                .modelRepositoryPath(directory.resolve("repository").toString())
                .shareOnnxSessionBetweenInstances(true).build(), client);
        try {
            for (var group : List.of("kind: KIND_CPU count: 3", "kind: KIND_GPU count: 3 gpus: [7]")) {
                var config = Files.writeString(directory.resolve("override.pbtxt"),
                        "platform: \"onnxruntime_onnx\"\ninstance_group { " + group + " }");
                var options = options(8).setGpuDevice(2).setModelConfigOverride(Optional.of(config)).build();
                var name = TritonOnnxRuntime.generateModelName(MODEL_PATH, options, true);
                try (var evaluator = runtime.evaluatorOf(MODEL_PATH, options)) {
                    var generated = TextFormat.parse(Files.readString(
                            directory.resolve("repository").resolve(name).resolve("config.pbtxt")), ModelConfig.class);
                    assertEquals(TextFormat.parse(Files.readString(config), ModelConfig.class).getInstanceGroup(0),
                                 generated.getInstanceGroup(0));
                }
            }
        } finally {
            runtime.deconstruct();
        }
    }

    @Test
    void non_explicit_modes_leave_options_and_repository_unchanged(@TempDir Path directory) throws IOException {
        var repository = Files.createDirectories(directory.resolve("repository"));
        // This would fail if the runtime tried to parse an override and generate a model config.
        var config = Files.writeString(repository.resolve("config.pbtxt"), "externally managed config");
        for (var mode : List.of(TritonConfig.ModelControlMode.NONE, TritonConfig.ModelControlMode.POLL)) {
            for (var options : List.of(options(8).build(), options(8).setGpuDevice(2, true).build(),
                                       options(8).setModelConfigOverride(Optional.of(config)).build())) {
                var client = mock(TritonOnnxClient.class);
                var runtime = new TritonOnnxRuntime(new TritonConfig.Builder().modelControlMode(mode)
                        .modelRepositoryPath(repository.toString()).shareOnnxSessionBetweenInstances(true).build(), client);
                var name = TritonOnnxRuntime.generateModelName(MODEL_PATH, options, true);
                try {
                    runtime.evaluatorOf(MODEL_PATH, options).close();
                    verify(client).getModelMetadata(name);
                    verifyNoMoreInteractions(client);
                } finally {
                    runtime.deconstruct();
                }
                verify(client).close();
                verifyNoMoreInteractions(client);
                assertEquals("externally managed config", Files.readString(config));
                try (var files = Files.list(repository)) {
                    assertEquals(List.of(config), files.toList());
                }
            }
        }
    }
}
