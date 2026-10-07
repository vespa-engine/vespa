// Copyright Vespa.ai. Licensed under the Apache License, Version 2.0.
package ai.vespa.triton;

import ai.vespa.llm.clients.TritonConfig;
import ai.vespa.modelintegration.evaluator.OnnxEvaluatorOptions;
import ai.vespa.modelintegration.evaluator.OnnxPoolingOptions;
import ai.vespa.modelintegration.evaluator.config.OnnxEvaluatorConfig;
import com.google.protobuf.TextFormat;
import inference.ModelConfigOuterClass.ModelConfig;
import inference.ModelConfigOuterClass.ModelInstanceGroup.Kind;
import onnx.Onnx;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static ai.vespa.triton.TritonOnnxRuntime.intraOpThreadsForSeparateSessions;
import static ai.vespa.triton.TritonOnnxRuntime.resolveInstanceCount;
import static inference.ModelConfigOuterClass.ModelInstanceGroup.Kind.KIND_AUTO;
import static inference.ModelConfigOuterClass.ModelInstanceGroup.Kind.KIND_CPU;
import static inference.ModelConfigOuterClass.ModelInstanceGroup.Kind.KIND_GPU;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests the defaults TritonOnnxRuntime derives from options. Does not need a Triton server.
 *
 * @author glebashnik
 * @author ingridhabrekke
 */
class TritonOnnxRuntimeDefaultsTest {

    private static final String MODEL_PATH = "src/test/models/onnx/transformer/dummy_transformer.onnx";

    private static final boolean SHARED_SESSION = true;
    private static final boolean SEPARATE_SESSIONS = false;

    private static OnnxEvaluatorOptions.Builder options(int cpuCores) {
        return new OnnxEvaluatorOptions.Builder(cpuCores);
    }

    private static boolean shouldPadForBatching(String modelPath, OnnxEvaluatorOptions options) {
        return TritonOnnxRuntime.shouldPadForBatching(modelPath, options);
    }

    @Test
    void inputs_are_padded_only_when_batching_on_gpu() {
        String float32 = "src/test/models/onnx/transformer/dummy_masked_attention_embedder.onnx";
        assertTrue(shouldPadForBatching(float32, options(16).setBatchingMaxSize(8).setGpuDevice(0).build()));
        assertTrue(shouldPadForBatching(float32, options(16).setBatchingMaxSize(8).setGpuDevice(0, true).build()));
        assertFalse(shouldPadForBatching(float32, options(16).setBatchingMaxSize(8).build()), "Should not pad on CPU");
        assertFalse(shouldPadForBatching(float32, options(16).setGpuDevice(0).build()), "Should not pad without batching");
        assertTrue(shouldPadForBatching("not-a-local-file", options(16).setBatchingMaxSize(8).setGpuDevice(0).build()));
    }

    @Test
    void padding_is_used_with_a_model_config_override_since_it_is_configured_on_purpose(@TempDir Path directory) throws IOException {
        String float32 = "src/test/models/onnx/transformer/dummy_masked_attention_embedder.onnx";
        var override = Files.writeString(directory.resolve("override.pbtxt"), "max_batch_size: 8 instance_group { kind: KIND_CPU }");
        assertTrue(shouldPadForBatching(float32, options(16).setModelConfigOverride(Optional.of(override)).build()),
                   "The override owns the batching setup, so padding configured with it is used as is");
    }

    @Test
    void padding_applies_by_default_since_triton_may_place_the_model_on_a_gpu() {
        String float32 = "src/test/models/onnx/transformer/dummy_masked_attention_embedder.onnx";
        var config = new OnnxEvaluatorConfig.Builder();
        config.batching.maxSize(8);
        assertTrue(shouldPadForBatching(float32, OnnxEvaluatorOptions.of(config.build(), 16)), "Should pad when no GPU device set, since Triton may place the model on a GPU.");
        assertFalse(shouldPadForBatching(float32, OnnxEvaluatorOptions.of(config.gpuDevice(-1).build(), 16)), "Should not pad on CPU (device -1).");
    }

    @Test
    void dynamically_quantized_models_are_not_padded() {
        String quantized = "src/test/models/onnx/transformer/embedding_model.onnx";
        assertFalse(shouldPadForBatching(quantized, options(16).setBatchingMaxSize(8).setGpuDevice(0).build()));
    }

    @Test
    void separate_sessions_get_one_instance_regardless_of_device() {
        for (int device : List.of(-1, 0, 2)) {
            var options = options(16).setGpuDevice(device).build();
            assertEquals(1, resolveInstanceCount(options, SEPARATE_SESSIONS).numModelInstances().orElseThrow());
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
                assertEquals(16, resolveInstanceCount(options, SHARED_SESSION).numModelInstances().orElseThrow());
            }
        }
        assertEquals(1, resolveInstanceCount(options(1).build(), SHARED_SESSION).numModelInstances().orElseThrow());
        assertEquals(1, resolveInstanceCount(options(0).build(), SHARED_SESSION).numModelInstances().orElseThrow());
    }

    @Test
    void explicit_instance_count_is_preserved() {
        var options = options(8).build().withNumModelInstances(3);
        assertSame(options, resolveInstanceCount(options, SHARED_SESSION));
        assertSame(options, resolveInstanceCount(options, SEPARATE_SESSIONS));
    }

    private static String modelName(OnnxEvaluatorOptions options, boolean shareSession) {
        var resolvedOptions = resolveInstanceCount(options, shareSession);
        return TritonOnnxRuntime.generateModelName(MODEL_PATH, resolvedOptions, shareSession, Optional.empty());
    }

    @Test
    void model_name_is_stable(@TempDir Path directory) throws IOException {
        // Naming only hashes the file contents, so a valid ONNX model is unnecessary.
        var model = Files.writeString(directory.resolve("model.onnx"), "model contents");
        var options = options(8).setGpuDevice(0).build().withNumModelInstances(2);

        // A fixed expected name detects changes in hashing or encoding across runs.
        assertEquals("model_98ad6a1dc766eee5",
                     TritonOnnxRuntime.generateModelName(model.toString(), options, SHARED_SESSION, Optional.empty()));

        // A model file without an extension keeps its whole name.
        var modelWithoutExtension = Files.writeString(directory.resolve("model"), "model contents");
        assertEquals("model_98ad6a1dc766eee5",
                     TritonOnnxRuntime.generateModelName(modelWithoutExtension.toString(), options, SHARED_SESSION, Optional.empty()));
    }

    @Test
    void model_name_accepts_unresolved_instance_counts_without_applying_defaults() {
        for (var options : List.of(options(8).build(), options(8).setGpuDevice(0).build())) {
            for (boolean shareSession : List.of(SEPARATE_SESSIONS, SHARED_SESSION)) {
                var name = assertDoesNotThrow(() ->
                        TritonOnnxRuntime.generateModelName(MODEL_PATH, options, shareSession, Optional.empty()));
                assertEquals(name, TritonOnnxRuntime.generateModelName(MODEL_PATH, options, shareSession, Optional.empty()));
                var defaultCount = TritonOnnxRuntime.defaultNumModelInstances(options, shareSession);
                assertNotEquals(name, TritonOnnxRuntime.generateModelName(
                        MODEL_PATH, options.withNumModelInstances(defaultCount), shareSession, Optional.empty()));
            }
        }
    }

    @Test
    void model_name_depends_on_file_contents(@TempDir Path directory) throws IOException {
        var model = Files.writeString(directory.resolve("model.onnx"), "model contents");
        var options = options(8).build().withNumModelInstances(1);
        var originalName = TritonOnnxRuntime.generateModelName(model.toString(), options,
                                                               SEPARATE_SESSIONS, Optional.empty());

        Files.writeString(model, "updated model contents");

        assertNotEquals(originalName,
                        TritonOnnxRuntime.generateModelName(model.toString(), options,
                                                            SEPARATE_SESSIONS, Optional.empty()));
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

    @ParameterizedTest
    @MethodSource("deviceOptions")
    void instance_group_respects_device_options(int device, boolean required, Kind expectedKind) {
        var group = TritonOnnxRuntime.createInstanceGroup(options(8).setGpuDevice(device, required).build(), 8);
        assertEquals(expectedKind, group.getKind());
        assertEquals(8, group.getCount());
        assertEquals(List.of(), group.getGpusList(), "The GPU list is left to Triton");
    }

    private static List<Arguments> deviceOptions() {
        return List.of(
                Arguments.of(-1, false, KIND_CPU),
                Arguments.of(0, false, KIND_AUTO),
                Arguments.of(2, false, KIND_AUTO),
                Arguments.of(-1, true, KIND_GPU),
                Arguments.of(0, true, KIND_GPU),
                Arguments.of(2, true, KIND_GPU));
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
                var name = TritonOnnxRuntime.generateModelName(MODEL_PATH, options, true, Optional.empty());
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
    void external_data_files_are_copied_next_to_the_model(@TempDir Path directory) throws IOException {
        var model = "src/test/models/onnx/external_data/add_with_external_data.onnx";
        var dataFile = Path.of("src/test/models/onnx/external_data/external_data.bin");
        var repository = directory.resolve("repository");
        var runtime = new TritonOnnxRuntime(new TritonConfig.Builder()
                .modelRepositoryPath(repository.toString())
                .shareOnnxSessionBetweenInstances(true).build(), mock(TritonOnnxClient.class));
        try {
            try (var evaluator = runtime.evaluatorOf(model, options(8).build());
                 var modelDirs = Files.list(repository)) {
                var versionDir = modelDirs.toList().get(0).resolve("1");
                assertTrue(Files.exists(versionDir.resolve("model.onnx")));
                assertArrayEquals(Files.readAllBytes(dataFile),
                                  Files.readAllBytes(versionDir.resolve("external_data.bin")));
            }
        } finally {
            runtime.deconstruct();
        }
    }

    @Test
    void external_data_file_named_model_onnx_is_rejected(@TempDir Path directory) throws IOException {
        var model = modelWithExternalData(directory, "model.onnx");
        Files.writeString(directory.resolve("model.onnx"), "weights");
        assertExternalDataRejected(directory, model, "cannot be named model.onnx");
    }

    @Test
    void external_data_file_outside_the_model_directory_is_rejected(@TempDir Path directory) throws IOException {
        var dataFile = Files.writeString(directory.resolve("weights.bin"), "weights");
        var absolute = modelWithExternalData(directory, dataFile.toAbsolutePath().toString());
        assertExternalDataRejected(directory, absolute, "is outside the directory of");

        var escaping = modelWithExternalData(directory, "../weights.bin");
        assertExternalDataRejected(directory, escaping, "is outside the directory of");
    }

    @Test
    void external_data_file_in_subdirectory_is_copied_to_the_same_subdirectory(@TempDir Path directory)
            throws IOException {
        var model = modelWithExternalData(directory, "weights/part0.bin");
        Files.createDirectories(directory.resolve("weights"));
        Files.writeString(directory.resolve("weights/part0.bin"), "weights");
        var repository = directory.resolve("repository");
        var runtime = runtimeWithRepository(repository);
        try {
            try (var evaluator = runtime.evaluatorOf(model.toString(), options(8).build());
                 var modelDirs = Files.list(repository)) {
                var dataFile = modelDirs.toList().get(0).resolve("1/weights/part0.bin");
                assertEquals("weights", Files.readString(dataFile));
            }
        } finally {
            runtime.deconstruct();
        }
    }

    private static Path modelWithExternalData(Path directory, String location) throws IOException {
        var tensor = Onnx.TensorProto.newBuilder()
                .setName("weights")
                .setDataLocation(Onnx.TensorProto.DataLocation.EXTERNAL)
                .addExternalData(Onnx.StringStringEntryProto.newBuilder().setKey("location").setValue(location));
        return Files.write(directory.resolve("encoder.onnx"), Onnx.ModelProto.newBuilder()
                .setGraph(Onnx.GraphProto.newBuilder().addInitializer(tensor)).build().toByteArray());
    }

    private static TritonOnnxRuntime runtimeWithRepository(Path repository) {
        return new TritonOnnxRuntime(new TritonConfig.Builder()
                .modelRepositoryPath(repository.toString())
                .shareOnnxSessionBetweenInstances(true).build(), mock(TritonOnnxClient.class));
    }

    private static void assertExternalDataRejected(Path directory, Path model, String expectedMessage) {
        var runtime = runtimeWithRepository(directory.resolve("repository"));
        try {
            var exception = assertThrows(IllegalArgumentException.class,
                                         () -> runtime.evaluatorOf(model.toString(), options(8).build()));
            assertTrue(exception.getMessage().contains(expectedMessage), exception.getMessage());
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
                var name = TritonOnnxRuntime.generateModelName(MODEL_PATH, options, true, Optional.empty());
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

    private static final OnnxPoolingOptions MEAN_POOLING =
            new OnnxPoolingOptions("mean", "last_hidden_state", "attention_mask");

    @Test
    void model_name_depends_on_pooling() {
        var options = options(8).build().withNumModelInstances(1);
        var name = TritonOnnxRuntime.generateModelName(MODEL_PATH, options, SHARED_SESSION, Optional.empty());

        var meanName = TritonOnnxRuntime.generateModelName(MODEL_PATH, options, SHARED_SESSION, Optional.of(MEAN_POOLING));
        var clsName = TritonOnnxRuntime.generateModelName(MODEL_PATH, options, SHARED_SESSION, Optional.of(
                new OnnxPoolingOptions("cls", "last_hidden_state", "attention_mask")));
        assertNotEquals(name, meanName);
        assertNotEquals(meanName, clsName);
    }

    @Test
    void model_name_depends_only_on_the_pooling_parameters_the_config_gets() {
        var options = options(8).build().withNumModelInstances(1);
        // The config leaves out the mask input for CLS and for the default name, so the mask does not tell models apart.
        assertEquals(modelName(options, "cls", "attention_mask"),
                     modelName(options, "cls", "mask"));
        assertNotEquals(modelName(options, "mean", "attention_mask"),
                        modelName(options, "mean", "mask"));
        assertEquals("pooling_output=last_hidden_state,pooling_strategy=cls",
                     TritonOnnxRuntime.poolingIdentity(new OnnxPoolingOptions("cls", "last_hidden_state", "mask")));
    }

    private static String modelName(OnnxEvaluatorOptions options, String strategy, String mask) {
        return TritonOnnxRuntime.generateModelName(MODEL_PATH, options, SHARED_SESSION,
                                                    Optional.of(new OnnxPoolingOptions(strategy, "last_hidden_state", mask)));
    }

    @Test
    void enabled_pooling_adds_parameters_to_generated_config(@TempDir Path directory) throws IOException {
        var repository = directory.resolve("repository");
        var runtime = new TritonOnnxRuntime(new TritonConfig.Builder()
                .modelRepositoryPath(repository.toString()).onnxPooling(true).build(), mock(TritonOnnxClient.class));
        var options = options(8).build();
        try (var evaluator = runtime.evaluatorOf(MODEL_PATH, options.withRuntimePooling(MEAN_POOLING))) {
            assertEquals(Optional.of(MEAN_POOLING), evaluator.runtimePooling());
            var name = TritonOnnxRuntime.generateModelName(
                    MODEL_PATH, resolveInstanceCount(options, SEPARATE_SESSIONS), SEPARATE_SESSIONS, Optional.of(MEAN_POOLING));
            var config = TextFormat.parse(Files.readString(repository.resolve(name).resolve("config.pbtxt")), ModelConfig.class);
            assertEquals(Map.of("pooling_strategy", "mean",
                                "pooling_output", "last_hidden_state",
                                "pooling_attention_mask", "attention_mask"),
                         poolingParameters(config));
        } finally {
            runtime.deconstruct();
        }
    }

    @Test
    void pooling_is_ignored_when_disabled_or_config_is_not_generated(@TempDir Path directory) throws IOException {
        var repository = directory.resolve("repository");
        var override = Files.writeString(directory.resolve("override.pbtxt"), "platform: \"onnxruntime_onnx\"");
        for (var enabled : List.of(false, true)) {
            for (var options : List.of(options(8).build(), options(8).setModelConfigOverride(Optional.of(override)).build())) {
                if (enabled && options.modelConfigOverride().isEmpty()) continue; // the case where pooling applies
                var runtime = new TritonOnnxRuntime(new TritonConfig.Builder()
                        .modelRepositoryPath(repository.toString()).onnxPooling(enabled).build(), mock(TritonOnnxClient.class));
                try (var evaluator = runtime.evaluatorOf(MODEL_PATH, options.withRuntimePooling(MEAN_POOLING))) {
                    assertTrue(evaluator.runtimePooling().isEmpty());
                    var resolved = options.modelConfigOverride().isPresent()
                            ? options : resolveInstanceCount(options, SEPARATE_SESSIONS);
                    var name = TritonOnnxRuntime.generateModelName(MODEL_PATH, resolved, SEPARATE_SESSIONS, Optional.empty());
                    var config = TextFormat.parse(Files.readString(repository.resolve(name).resolve("config.pbtxt")), ModelConfig.class);
                    assertEquals(Map.of(), poolingParameters(config));
                } finally {
                    runtime.deconstruct();
                }
            }
        }
        for (var mode : List.of(TritonConfig.ModelControlMode.NONE, TritonConfig.ModelControlMode.POLL)) {
            var runtime = new TritonOnnxRuntime(new TritonConfig.Builder().modelControlMode(mode)
                    .modelRepositoryPath(repository.toString()).onnxPooling(true).build(), mock(TritonOnnxClient.class));
            try (var evaluator = runtime.evaluatorOf(MODEL_PATH, options(8).build().withRuntimePooling(MEAN_POOLING))) {
                assertTrue(evaluator.runtimePooling().isEmpty());
            } finally {
                runtime.deconstruct();
            }
        }
    }

    private static Map<String, String> poolingParameters(ModelConfig config) {
        var result = new TreeMap<String, String>();
        config.getParametersMap().forEach((key, value) -> {
            if (key.startsWith("pooling_")) result.put(key, value.getStringValue());
        });
        return result;
    }
}
