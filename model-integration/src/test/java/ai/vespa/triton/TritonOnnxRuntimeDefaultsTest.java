// Copyright Vespa.ai. Licensed under the Apache License, Version 2.0.
package ai.vespa.triton;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import ai.vespa.llm.clients.TritonConfig;
import ai.vespa.modelintegration.evaluator.BatchDimensionTestModels;
import ai.vespa.modelintegration.evaluator.OnnxEvaluatorOptions;
import ai.vespa.modelintegration.evaluator.OnnxPoolingOptions;
import ai.vespa.modelintegration.evaluator.config.OnnxEvaluatorConfig;
import com.google.protobuf.TextFormat;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.tensor.TensorType;
import inference.ModelConfigOuterClass.DataType;
import inference.ModelConfigOuterClass.ModelConfig;
import inference.ModelConfigOuterClass.ModelInput;
import inference.ModelConfigOuterClass.ModelOutput;
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
import java.util.Arrays;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
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
    // The message Triton 26.09 returns.
    private static final String REJECTS_BATCHING = "INVALID_ARGUMENT: load failed for model 'model': version 1 is at UNAVAILABLE state: " +
            "Invalid argument: autofill failed for model 'model': model does not support batching while non-zero max_batch_size is specified;";

    private static final boolean SHARED_SESSION = true;
    private static final boolean SEPARATE_SESSIONS = false;

    // Options as embedders build them. Tests of other models set firstDimensionIsBatch to false.
    private static OnnxEvaluatorOptions.Builder options(int cpuCores) {
        return new OnnxEvaluatorOptions.Builder(cpuCores).setFirstDimensionIsBatch(true);
    }

    private static boolean shouldPadForBatching(String modelPath, OnnxEvaluatorOptions options) {
        return TritonOnnxRuntime.shouldPadForBatching(modelPath, options, () -> TritonOnnxRuntime.hasDynamicFirstDimension(modelPath));
    }

    @Test
    void inputs_are_padded_only_when_batching_on_gpu() {
        String float32 = "src/test/models/onnx/transformer/dummy_masked_attention_embedder.onnx";
        assertTrue(shouldPadForBatching(float32, options(16).setBatchingMaxSize(8).setGpuDevice(0).build()));
        assertTrue(shouldPadForBatching(float32, options(16).setBatchingMaxSize(8).setGpuDevice(0, true).build()));
        assertFalse(shouldPadForBatching(float32, options(16).setBatchingMaxSize(8).build()), "Should not pad on CPU");
        assertFalse(shouldPadForBatching(float32, options(16).setGpuDevice(0).build()), "Should not pad without batching");
        assertFalse(shouldPadForBatching(float32, options(16).setBatchingMaxSize(0).setGpuDevice(0).build()), "Should not pad without batching");
        assertTrue(shouldPadForBatching("not-a-local-file", options(16).setBatchingMaxSize(8).setGpuDevice(0).build()));
    }

    // Triton loads a model config that specifies inputs and outputs only if they match the shapes ONNX Runtime reports.
    // The test therefore checks the specified shapes against a real ONNX Runtime session.
    @Test
    void specifies_inputs_and_outputs_with_the_shapes_onnx_runtime_reports(@TempDir Path directory) throws Exception {
        for (var testCase : BatchDimensionTestModels.cases()) {
            var model = Files.write(directory.resolve(testCase.name() + ".onnx"), testCase.model().toByteArray()).toString();
            var specified = TritonOnnxRuntime.declaredInputsAndOutputsConfig(model);
            // An input or output without a declared shape cannot be specified.
            if (testCase.name().startsWith("unshaped_")) {
                assertTrue(specified.isEmpty(), testCase.name());
                continue;
            }
            try (var options = new OrtSession.SessionOptions();
                 var session = OrtEnvironment.getEnvironment().createSession(model, options)) {
                var inputShapes = new TreeMap<String, List<Long>>();
                specified.orElseThrow().getInputList().forEach(input -> inputShapes.put(input.getName(), input.getDimsList()));
                var outputShapes = new TreeMap<String, List<Long>>();
                specified.orElseThrow().getOutputList().forEach(output -> outputShapes.put(output.getName(), output.getDimsList()));
                assertEquals(onnxRuntimeShapes(session.getInputInfo()), inputShapes, testCase.name());
                assertEquals(onnxRuntimeShapes(session.getOutputInfo()), outputShapes, testCase.name());
            }
        }
    }

    @Test
    void specifies_the_element_types_as_triton_names_them(@TempDir Path directory) throws IOException {
        var model = Files.write(directory.resolve("cross_encoder.onnx"),
                                BatchDimensionTestModels.crossEncoder(true).toByteArray()).toString();
        var specified = TritonOnnxRuntime.declaredInputsAndOutputsConfig(model).orElseThrow();
        assertEquals(List.of(DataType.TYPE_INT64, DataType.TYPE_INT64),
                     specified.getInputList().stream().map(ModelInput::getDataType).toList());
        assertEquals(List.of(DataType.TYPE_FP32), specified.getOutputList().stream().map(ModelOutput::getDataType).toList());
        assertTrue(TritonOnnxRuntime.declaredInputsAndOutputsConfig("not-a-local-file").isEmpty());

        // An element type newer than the ONNX protobuf definition in Vespa cannot be specified.
        var unknownType = Onnx.ModelProto.newBuilder().setGraph(Onnx.GraphProto.newBuilder()
                .addInput(valueInfo("x", 99)).addOutput(valueInfo("y", 1))).build();
        var unknownTypeModel = Files.write(directory.resolve("unknown_type.onnx"), unknownType.toByteArray()).toString();
        assertTrue(TritonOnnxRuntime.declaredInputsAndOutputsConfig(unknownTypeModel).isEmpty());
    }

    // A model config without inputs does not stop Triton's auto-complete from replacing max batch size 0.
    @Test
    void specifies_nothing_for_a_model_whose_only_input_is_an_initializer(@TempDir Path directory) throws IOException {
        var onlyInitializer = Onnx.ModelProto.newBuilder().setGraph(Onnx.GraphProto.newBuilder()
                .addInput(valueInfo("w", 1)).addOutput(valueInfo("y", 1))
                .addInitializer(Onnx.TensorProto.newBuilder().setName("w").setDataType(1).addDims(1).addFloatData(1))).build();
        var model = Files.write(directory.resolve("only_initializer.onnx"), onlyInitializer.toByteArray()).toString();
        assertTrue(TritonOnnxRuntime.declaredInputsAndOutputsConfig(model).isEmpty());
    }

    private static Onnx.ValueInfoProto valueInfo(String name, int elemType) {
        return Onnx.ValueInfoProto.newBuilder().setName(name).setType(Onnx.TypeProto.newBuilder().setTensorType(
                Onnx.TypeProto.Tensor.newBuilder().setElemType(elemType).setShape(Onnx.TensorShapeProto.newBuilder()
                        .addDim(Onnx.TensorShapeProto.Dimension.newBuilder().setDimParam("batch"))))).build();
    }

    private static Map<String, List<Long>> onnxRuntimeShapes(Map<String, NodeInfo> infos) {
        var shapes = new TreeMap<String, List<Long>>();
        infos.forEach((name, info) -> shapes.put(name, Arrays.stream(((TensorInfo) info.getInfo()).getShape()).boxed().toList()));
        return shapes;
    }

    @Test
    void models_without_batch_dimension_are_not_padded_since_they_are_not_batched() {
        var options = options(16).setBatchingMaxSize(8).setGpuDevice(0).build();
        assertFalse(shouldPadForBatching("src/test/models/onnx/add_float.onnx", options));
        assertFalse(shouldPadForBatching("src/test/models/onnx/catboost/catboost_regressor.onnx", options));
    }

    @Test
    void rerankers_are_padded_and_batched_only_with_a_batch_dimension(@TempDir Path directory) throws IOException {
        var batched = Files.write(directory.resolve("cross_encoder.onnx"),
                                  BatchDimensionTestModels.crossEncoder(true).toByteArray()).toString();
        var fixed = Files.write(directory.resolve("cross_encoder_fixed.onnx"),
                                BatchDimensionTestModels.crossEncoder(false).toByteArray()).toString();
        var batchingOnGpu = options(16).setBatchingMaxSize(8).setGpuDevice(0).build();
        assertTrue(shouldPadForBatching(batched, batchingOnGpu));
        assertFalse(shouldPadForBatching(fixed, batchingOnGpu));

        // RankProfilesConfigImporter builds these options for global-phase ranking, without batching or padding
        var globalPhase = new OnnxEvaluatorOptions.Builder().setGpuDevice(0, false).build();
        assertFalse(shouldPadForBatching(batched, globalPhase));
        assertTrue(TritonOnnxRuntime.hasDynamicFirstDimension(batched));
        assertFalse(TritonOnnxRuntime.hasDynamicFirstDimension(fixed));
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
        assertTrue(shouldPadForBatching(float32, OnnxEvaluatorOptions.builder(config.build(), 16).build()), "Should pad when no GPU device set, since Triton may place the model on a GPU.");
        assertFalse(shouldPadForBatching(float32, OnnxEvaluatorOptions.builder(config.gpuDevice(-1).build(), 16).build()), "Should not pad on CPU (device -1).");
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
        var options = options(8).setNumModelInstances(3).build();
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
        var options = options(8).setGpuDevice(0).setNumModelInstances(2).build();

        // A fixed expected name detects changes in hashing or encoding across runs.
        assertEquals("model_98ad6a1dc766eee5",
                     TritonOnnxRuntime.generateModelName(model.toString(), options, SHARED_SESSION, Optional.empty()));

        // The pooling parameters are part of the name, in their own encoding.
        assertEquals("model_614d5bfbd056302f",
                     TritonOnnxRuntime.generateModelName(model.toString(), options, SHARED_SESSION, Optional.of(MEAN_POOLING)));

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
                        MODEL_PATH, options.toBuilder().setNumModelInstances(defaultCount).build(), shareSession, Optional.empty()));
            }
        }
    }

    @Test
    void model_name_depends_on_file_contents(@TempDir Path directory) throws IOException {
        var model = Files.writeString(directory.resolve("model.onnx"), "model contents");
        var options = options(8).setNumModelInstances(1).build();
        var originalName = TritonOnnxRuntime.generateModelName(model.toString(), options,
                                                               SEPARATE_SESSIONS, Optional.empty());

        Files.writeString(model, "updated model contents");

        assertNotEquals(originalName,
                        TritonOnnxRuntime.generateModelName(model.toString(), options,
                                                            SEPARATE_SESSIONS, Optional.empty()));
    }

    @Test
    void model_name_depends_on_session_sharing_with_the_same_instance_count() {
        var options = options(8).setNumModelInstances(1).build();

        assertNotEquals(modelName(options, SEPARATE_SESSIONS),
                        modelName(options, SHARED_SESSION));
    }

    @Test
    void model_name_depends_on_thread_count() {
        var options = options(8).setNumModelInstances(1).build();
        var moreThreads = options.toBuilder().setIntraOpThreads(4).build();

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

    // ONNX Runtime's shape inference can remove a dynamic first dimension that the model file declares.
    // Triton then rejects batching, and the runtime loads the model again with a max batch size of 0.
    @Test
    void model_is_loaded_again_without_batching_when_triton_rejects_batching(@TempDir Path directory) throws IOException {
        var model = Files.write(directory.resolve("reduced_output.onnx"),
                                BatchDimensionTestModels.casesWhereShapeInferenceRemovesBatchDimension().get(0).model().toByteArray()).toString();
        var options = options(8).setBatchingMaxSize(8).build();
        var name = TritonOnnxRuntime.generateModelName(model, resolveInstanceCount(options, true), true, Optional.empty());
        var repository = directory.resolve("repository");
        var client = mock(TritonOnnxClient.class);
        doThrow(new TritonOnnxClient.TritonException(REJECTS_BATCHING)).doNothing().when(client).loadUntilModelReady(name);
        var runtime = new TritonOnnxRuntime(new TritonConfig.Builder()
                .modelRepositoryPath(repository.toString()).shareOnnxSessionBetweenInstances(true).build(), client);
        try (var evaluator = runtime.evaluatorOf(model, options)) {
            verify(client, times(2)).loadUntilModelReady(name);
            var config = TextFormat.parse(Files.readString(repository.resolve(name).resolve("config.pbtxt")), ModelConfig.class);
            assertEquals(0, config.getMaxBatchSize());
            assertFalse(config.hasDynamicBatching());
            // Padding follows the max batch size Triton loaded the model with, not the shapes in the model file
            assertEquals(Optional.of(false), runtime.hasNonZeroMaxBatchSize(name));
            assertTrue(TritonOnnxRuntime.hasDynamicFirstDimension(model));
        } finally {
            runtime.deconstruct();
        }
    }

    @Test
    void models_whose_first_dimension_is_not_a_batch_load_with_max_batch_size_0(@TempDir Path directory) throws IOException {
        var model = Files.write(directory.resolve("pairwise_similarity.onnx"),
                                BatchDimensionTestModels.pairwiseSimilarity().toByteArray()).toString();
        var options = options(8).setFirstDimensionIsBatch(false).build();
        var name = TritonOnnxRuntime.generateModelName(model, resolveInstanceCount(options, true), true, Optional.empty());
        var repository = directory.resolve("repository");
        var client = mock(TritonOnnxClient.class);
        var runtime = new TritonOnnxRuntime(new TritonConfig.Builder()
                .modelRepositoryPath(repository.toString()).shareOnnxSessionBetweenInstances(true).build(), client);
        try (var evaluator = runtime.evaluatorOf(model, options)) {
            var config = TextFormat.parse(Files.readString(repository.resolve(name).resolve("config.pbtxt")), ModelConfig.class);
            assertEquals(0, config.getMaxBatchSize());
            assertEquals(1, config.getInputCount());
            assertEquals(1, config.getOutputCount());
            assertFalse(config.hasDynamicBatching());
            assertEquals(Optional.of(false), runtime.hasNonZeroMaxBatchSize(name));
        } finally {
            runtime.deconstruct();
        }
    }

    // Max batch size 1 rejects requests with more than one row, so only a rejected config falls back to it.
    // Other errors, such as Triton being unavailable, fail the load.
    @Test
    void models_whose_first_dimension_is_not_a_batch_fall_back_only_when_triton_rejects_the_config(@TempDir Path directory)
            throws IOException {
        var model = Files.write(directory.resolve("pairwise_similarity.onnx"),
                                BatchDimensionTestModels.pairwiseSimilarity().toByteArray()).toString();
        var options = options(8).setFirstDimensionIsBatch(false).build();
        var name = TritonOnnxRuntime.generateModelName(model, resolveInstanceCount(options, true), true, Optional.empty());
        var rejectsSpecifiedInputsAndOutputs = "INVALID_ARGUMENT: load failed for model 'model': version 1 is at UNAVAILABLE state: " +
                "Invalid argument: unable to load model 'model', configuration expects datatype TYPE_FP16 for input 'documents', " +
                "model provides TYPE_FP32;";
        for (var error : List.of(rejectsSpecifiedInputsAndOutputs, "UNAVAILABLE: io exception: Connection refused")) {
            var repository = directory.resolve("repository-" + error.hashCode());
            var client = mock(TritonOnnxClient.class);
            doThrow(new TritonOnnxClient.TritonException(error)).doNothing().when(client).loadUntilModelReady(name);
            var runtime = new TritonOnnxRuntime(new TritonConfig.Builder()
                    .modelRepositoryPath(repository.toString()).shareOnnxSessionBetweenInstances(true).build(), client);
            try {
                if (error.equals(rejectsSpecifiedInputsAndOutputs)) {
                    try (var evaluator = runtime.evaluatorOf(model, options)) {
                        verify(client, times(2)).loadUntilModelReady(name);
                        var config = TextFormat.parse(Files.readString(repository.resolve(name).resolve("config.pbtxt")),
                                                      ModelConfig.class);
                        assertEquals(1, config.getMaxBatchSize());
                        assertEquals(0, config.getInputCount());
                        assertFalse(config.hasDynamicBatching());
                        assertEquals(Optional.of(true), runtime.hasNonZeroMaxBatchSize(name));
                    }
                } else {
                    assertThrows(TritonOnnxClient.TritonException.class, () -> runtime.evaluatorOf(model, options));
                    verify(client, times(1)).loadUntilModelReady(name);
                }
            } finally {
                runtime.deconstruct();
            }
        }
    }

    @Test
    void model_is_not_loaded_again_for_other_errors_or_with_a_model_config_override(@TempDir Path directory)
            throws IOException {
        var model = Files.write(directory.resolve("reduced_output.onnx"),
                                BatchDimensionTestModels.casesWhereShapeInferenceRemovesBatchDimension().get(0).model().toByteArray()).toString();
        var override = Files.writeString(directory.resolve("override.pbtxt"), "max_batch_size: 8");
        var cases = Map.of(options(8).setBatchingMaxSize(8).build(), "some other error",
                           options(8).setModelConfigOverride(Optional.of(override)).build(), REJECTS_BATCHING);
        for (var entry : cases.entrySet()) {
            var options = entry.getKey();
            var resolved = options.modelConfigOverride().isPresent() ? options : resolveInstanceCount(options, true);
            var name = TritonOnnxRuntime.generateModelName(model, resolved, true, Optional.empty());
            var client = mock(TritonOnnxClient.class);
            doThrow(new TritonOnnxClient.TritonException(entry.getValue())).when(client).loadUntilModelReady(name);
            var runtime = new TritonOnnxRuntime(new TritonConfig.Builder()
                    .modelRepositoryPath(directory.resolve("repository").toString())
                    .shareOnnxSessionBetweenInstances(true).build(), client);
            try {
                assertThrows(TritonOnnxClient.TritonException.class, () -> runtime.evaluatorOf(model, options));
                verify(client, times(1)).loadUntilModelReady(name);
            } finally {
                runtime.deconstruct();
            }
        }
    }

    // Triton fails to load a model whose inputs or outputs have no dynamic first dimension with a non-zero max batch size.
    @Test
    void model_without_batch_dimension_is_not_batched(@TempDir Path directory) throws IOException {
        var unbatched = "src/test/models/onnx/add_float.onnx";
        var repository = directory.resolve("repository");
        var runtime = new TritonOnnxRuntime(new TritonConfig.Builder()
                .modelRepositoryPath(repository.toString())
                .shareOnnxSessionBetweenInstances(true).build(), mock(TritonOnnxClient.class));
        try {
            for (var batchingMaxSize : List.of(1, 8)) {
                var options = options(8).setBatchingMaxSize(batchingMaxSize).build();
                for (var model : List.of(unbatched, MODEL_PATH)) {
                    var name = TritonOnnxRuntime.generateModelName(
                            model, resolveInstanceCount(options, true), true, Optional.empty());
                    try (var evaluator = runtime.evaluatorOf(model, options)) {
                        var config = TextFormat.parse(Files.readString(
                                repository.resolve(name).resolve("config.pbtxt")), ModelConfig.class);
                        assertEquals(model.equals(unbatched) ? 0 : batchingMaxSize, config.getMaxBatchSize(), model);
                        assertEquals(!model.equals(unbatched) && batchingMaxSize > 1, config.hasDynamicBatching(), model);
                    }
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
            new OnnxPoolingOptions(OnnxPoolingOptions.Strategy.MEAN, "last_hidden_state", "attention_mask");

    @Test
    void model_name_depends_on_pooling() {
        var options = options(8).setNumModelInstances(1).build();
        var name = TritonOnnxRuntime.generateModelName(MODEL_PATH, options, SHARED_SESSION, Optional.empty());

        var meanName = TritonOnnxRuntime.generateModelName(MODEL_PATH, options, SHARED_SESSION, Optional.of(MEAN_POOLING));
        var clsName = TritonOnnxRuntime.generateModelName(MODEL_PATH, options, SHARED_SESSION, Optional.of(
                new OnnxPoolingOptions(OnnxPoolingOptions.Strategy.CLS, "last_hidden_state", "attention_mask")));
        assertNotEquals(name, meanName);
        assertNotEquals(meanName, clsName);
    }

    @Test
    void model_name_depends_only_on_the_pooling_parameters_the_config_gets() {
        var options = options(8).setNumModelInstances(1).build();
        // The config leaves out the mask input for CLS and for the default name, so the mask does not tell models apart.
        assertEquals(modelName(options, OnnxPoolingOptions.Strategy.CLS, "attention_mask"),
                     modelName(options, OnnxPoolingOptions.Strategy.CLS, "mask"));
        assertNotEquals(modelName(options, OnnxPoolingOptions.Strategy.MEAN, "attention_mask"),
                        modelName(options, OnnxPoolingOptions.Strategy.MEAN, "mask"));
        assertEquals("pooling_output=last_hidden_state,pooling_strategy=cls",
                     TritonOnnxRuntime.poolingIdentity(new OnnxPoolingOptions(OnnxPoolingOptions.Strategy.CLS, "last_hidden_state", "mask")));
    }

    private static String modelName(OnnxEvaluatorOptions options, OnnxPoolingOptions.Strategy strategy, String mask) {
        return TritonOnnxRuntime.generateModelName(MODEL_PATH, options, SHARED_SESSION,
                                                    Optional.of(new OnnxPoolingOptions(strategy, "last_hidden_state", mask)));
    }

    @Test
    void enabled_pooling_adds_parameters_to_generated_config(@TempDir Path directory) throws IOException {
        var repository = directory.resolve("repository");
        var client = mock(TritonOnnxClient.class);
        when(client.getModelMetadata(any())).thenReturn(pooledMetadata());
        var runtime = new TritonOnnxRuntime(new TritonConfig.Builder()
                .modelRepositoryPath(repository.toString()).onnxPooling(true).build(), client);
        var options = options(8).build();
        try (var evaluator = runtime.evaluatorOf(MODEL_PATH, options.toBuilder().setRuntimePooling(Optional.of(MEAN_POOLING)).build())) {
            assertEquals(Optional.of(MEAN_POOLING), evaluator.runtimePooling());
            var name = TritonOnnxRuntime.generateModelName(
                    MODEL_PATH, resolveInstanceCount(options, SEPARATE_SESSIONS), SEPARATE_SESSIONS, Optional.of(MEAN_POOLING));
            var config = TextFormat.parse(Files.readString(repository.resolve(name).resolve("config.pbtxt")), ModelConfig.class);
            // The config leaves the default mask input to Triton, which also handles a model without it.
            assertEquals(Map.of("pooling_strategy", "mean", "pooling_output", "last_hidden_state"),
                         poolingParameters(config));
        } finally {
            runtime.deconstruct();
        }
    }

    /** The metadata of a model whose output Triton pooled to [batch, embedding] */
    private static TritonOnnxClient.ModelMetadata pooledMetadata() {
        var tokens = TensorType.fromSpec("tensor<float>(d0[],d1[])");
        return new TritonOnnxClient.ModelMetadata(Map.of("input_ids", tokens, "attention_mask", tokens),
                                                  Map.of("last_hidden_state", TensorType.fromSpec("tensor<float>(d0[],d1[4])")));
    }

    @Test
    void evaluator_fails_when_triton_did_not_pool() {
        var client = mock(TritonOnnxClient.class);
        var tokenEmbeddings = TensorType.fromSpec("tensor<float>(d0[],d1[],d2[4])");
        when(client.getModelMetadata(any())).thenReturn(new TritonOnnxClient.ModelMetadata(Map.of(), Map.of("last_hidden_state", tokenEmbeddings)));
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new TritonOnnxEvaluator("model_1", mock(ResourceReference.class), client, true, Optional.of(MEAN_POOLING)));
        assertEquals("Triton did not pool output 'last_hidden_state' of model model_1: got type " + tokenEmbeddings +
                     " instead of [batch, embedding]. The Triton image does not support pooling. " +
                     "Disable triton-onnx-pooling, or use an image whose ONNX Runtime backend supports it.",
                     exception.getMessage());
        // A missing output is left to the caller, which reports it
        assertDoesNotThrow(() -> new TritonOnnxEvaluator("model_1", mock(ResourceReference.class), client, true,
                                                         Optional.of(new OnnxPoolingOptions(OnnxPoolingOptions.Strategy.MEAN, "no_such_output", "attention_mask"))));
    }

    @Test
    void pooling_parameters_name_only_a_non_default_mask_used_by_the_strategy() {
        assertEquals(Map.of("pooling_strategy", "mean", "pooling_output", "token_embeddings",
                            "pooling_attention_mask", "mask"),
                     poolingParameters(OnnxPoolingOptions.Strategy.MEAN, "token_embeddings", "mask"));
        assertEquals(Map.of("pooling_strategy", "last", "pooling_output", "last_hidden_state",
                            "pooling_attention_mask", "mask"),
                     poolingParameters(OnnxPoolingOptions.Strategy.LAST, "last_hidden_state", "mask"));
        assertEquals(Map.of("pooling_strategy", "last", "pooling_output", "last_hidden_state"),
                     poolingParameters(OnnxPoolingOptions.Strategy.LAST, "last_hidden_state", "attention_mask"));
        // CLS pooling does not use the mask.
        assertEquals(Map.of("pooling_strategy", "cls", "pooling_output", "last_hidden_state"),
                     poolingParameters(OnnxPoolingOptions.Strategy.CLS, "last_hidden_state", "mask"));
    }

    private static Map<String, String> poolingParameters(OnnxPoolingOptions.Strategy strategy, String output, String mask) {
        var builder = ModelConfig.newBuilder();
        TritonOnnxRuntime.addPoolingParameters(builder, new OnnxPoolingOptions(strategy, output, mask));
        return poolingParameters(builder.build());
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
                try (var evaluator = runtime.evaluatorOf(MODEL_PATH, options.toBuilder().setRuntimePooling(Optional.of(MEAN_POOLING)).build())) {
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
            try (var evaluator = runtime.evaluatorOf(MODEL_PATH, options(8).setRuntimePooling(Optional.of(MEAN_POOLING)).build())) {
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
