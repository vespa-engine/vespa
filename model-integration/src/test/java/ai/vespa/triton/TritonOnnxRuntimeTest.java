// Copyright Vespa.ai. Licensed under the Apache License, Version 2.0.
package ai.vespa.triton;

import ai.vespa.llm.clients.TritonConfig;
import ai.vespa.modelintegration.evaluator.OnnxEvaluator;
import ai.vespa.modelintegration.evaluator.OnnxEvaluatorOptions;
import com.yahoo.io.IOUtils;
import com.yahoo.text.Text;
import com.yahoo.yolean.Exceptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static inference.ModelConfigOuterClass.ModelInstanceGroup.Kind.KIND_CPU;
import static inference.ModelConfigOuterClass.ModelInstanceGroup.Kind.KIND_GPU;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.function.ThrowingSupplier;

/**
 * @author bjorncs
 * @author glebashnik
 */
@EnabledIfSystemProperty(named = "VESPA_USE_TRITON", matches = "true")
@ExtendWith(ContainerEnvironmentAvailableCondition.class)
class TritonOnnxRuntimeTest {

    private static final String MODEL_PATH = "src/test/models/onnx/transformer/dummy_transformer.onnx";

    private static TritonServerContainer tritonContainer;
    private final OnnxEvaluatorOptions.Builder optsBuilder =
            new OnnxEvaluatorOptions.Builder(8).setOptimizeModel(true);

    // Used by most of the test. Some use their own.
    @BeforeAll
    static void setupTritonContainer() throws IOException {
        tritonContainer = new TritonServerContainer();
        tritonContainer.start();
    }


    @Test
    void load_model_with_defaults() throws IOException {
        var opts = optsBuilder.build();
        assertLoadModel("src/test/triton/config_with_defaults.pbtxt", opts);
    }

    @Test
    void load_model_with_threads() throws IOException {
        // Without session sharing, the intra-op threads are the CPU cores divided between the instances.
        // The configured 16 is not used.
        var opts = optsBuilder.setThreadsFromFactors(8, 16).build();
        assertLoadModel("src/test/triton/config_with_threads.pbtxt", opts);
    }

    @Test
    void load_model_with_execution_mode() throws IOException {
        var opts = optsBuilder.setExecutionMode(OnnxEvaluatorOptions.ExecutionMode.PARALLEL).build();
        assertLoadModel("src/test/triton/config_with_execution_mode.pbtxt", opts);
    }

    @Test
    void load_model_without_optimization() throws IOException {
        var opts = optsBuilder.setOptimizeModel(false).build();
        assertLoadModel("src/test/triton/config_without_optimization.pbtxt", opts);
    }

    @Test
    void load_model_with_batching() throws IOException {
        var opts = optsBuilder
                .setBatchingMaxSize(10)
                .build();
        assertLoadModel("src/test/triton/config_with_batching.pbtxt", opts);
    }

    @Test
    void load_model_with_absolute_concurrency() throws IOException {
        var opts = optsBuilder
                .setConcurrency(2, OnnxEvaluatorOptions.ConcurrencyFactorType.ABSOLUTE)
                .build();
        assertLoadModel("src/test/triton/config_with_absolute_concurrency.pbtxt", opts);
    }

    @Test
    void load_model_with_relative_concurrency() throws IOException {
        var opts = optsBuilder
                .setConcurrency(1.5, OnnxEvaluatorOptions.ConcurrencyFactorType.RELATIVE)
                .build();
        assertLoadModel("src/test/triton/config_with_relative_concurrency.pbtxt", opts);
    }

    @Test
    void load_model_with_shared_session() throws IOException {
        var opts = optsBuilder
                .setConcurrency(2, OnnxEvaluatorOptions.ConcurrencyFactorType.ABSOLUTE)
                .build();
        assertLoadModel("src/test/triton/config_with_shared_session.pbtxt", opts, true);
    }

    @Test
    void uses_gpu_when_available_otherwise_cpu() throws IOException {
        try (var client = createClient()) {
            var devices = new TritonGpuProbe(client).gpuDevices();
            var device = devices.stream().min(Integer::compareTo).orElse(0);
            var opts = optsBuilder.setGpuDevice(device).build();
            var resolved = TritonOnnxRuntime.resolveOptions(opts, false, devices);
            var name = TritonOnnxRuntime.generateModelName(MODEL_PATH, resolved, false);
            var runtime = createRuntime();
            try (var evaluator = runtime.evaluatorOf(MODEL_PATH, opts)) {
                var group = client.getModelConfig(name).getInstanceGroup(0);
                assertEquals(devices.isEmpty() ? KIND_CPU : KIND_GPU, group.getKind());
                assertEquals(devices.isEmpty() ? List.of() : List.of(device), group.getGpusList());
                assertRepoFileCount(tritonContainer, 1); // No probe files in the shared repository
            } finally {
                runtime.deconstruct();
            }
        }
    }

    @Test
    void falls_back_to_cpu_when_no_gpus_are_available() {
        try (var client = createClient()) {
            assumeTrue(new TritonGpuProbe(client).gpuDevices().isEmpty(), "Requires a CPU-only Triton server");
            var opts = optsBuilder.setGpuDevice(Integer.MAX_VALUE).build();
            var runtime = createRuntime();
            try {
                runtime.evaluatorOf(MODEL_PATH, opts).close();
                assertRepoFileCount(tritonContainer, 0);
            } finally {
                runtime.deconstruct();
            }
        }
    }

    @Test
    void rejects_unknown_gpu_id_when_gpus_are_available() {
        try (var client = createClient()) {
            assumeFalse(new TritonGpuProbe(client).gpuDevices().isEmpty(), "Requires a Triton server with GPUs");
            var opts = optsBuilder.setGpuDevice(Integer.MAX_VALUE).build();
            var runtime = createRuntime();
            try {
                assertThrows(TritonOnnxClient.TritonException.class, () -> runtime.evaluatorOf(MODEL_PATH, opts));
                assertRepoFileCount(tritonContainer, 0);
            } finally {
                runtime.deconstruct();
            }
        }
    }

    @Test
    void fails_when_required_gpu_is_unavailable() {
        var opts = optsBuilder.setGpuDevice(Integer.MAX_VALUE, true).build();
        var runtime = createRuntime();
        try {
            assertThrows(TritonOnnxClient.TritonException.class, () -> runtime.evaluatorOf(MODEL_PATH, opts));
            assertRepoFileCount(tritonContainer, 0);
        } finally {
            runtime.deconstruct();
        }
    }

    // Sharing a session between instances keeps one copy of the model.
    // The default is then one instance per CPU core.
    // The thread counts are the configured ones, like for EmbeddedOnnxRuntime.
    @Test
    void load_model_with_shared_session_defaults() throws IOException {
        var opts = optsBuilder.build();
        assertLoadModel("src/test/triton/config_with_shared_session_defaults.pbtxt", opts, true);
    }

    @Test
    void load_model_with_shared_session_and_threads() throws IOException {
        // Sequential execution uses one inter-op thread
        var opts = optsBuilder.setThreadsFromFactors(8, 16).build();
        assertLoadModel("src/test/triton/config_with_shared_session_threads.pbtxt", opts, true);
    }

    @Test
    void load_model_with_shared_session_and_execution_mode() throws IOException {
        var opts = optsBuilder.setExecutionMode(OnnxEvaluatorOptions.ExecutionMode.PARALLEL).build();
        assertLoadModel("src/test/triton/config_with_shared_session_execution_mode.pbtxt", opts, true);
    }

    @Test
    void load_model_with_model_config_override() throws IOException {
        var configPathInput = "src/test/triton/config_with_model_config_override_input.pbtxt";
        var configPathOutput = "src/test/triton/config_with_model_config_override_output.pbtxt";
        var opts = optsBuilder
                .setModelConfigOverride(Optional.of(Path.of(configPathInput)))
                .build();
        assertLoadModel(configPathOutput, opts);
    }

    @Test
    void load_model_with_model_config_override_and_shared_session() throws IOException {
        var configPathInput = "src/test/triton/config_with_model_config_override_input.pbtxt";
        var configPathOutput = "src/test/triton/config_with_model_config_override_shared_session_output.pbtxt";
        var opts = optsBuilder
                .setModelConfigOverride(Optional.of(Path.of(configPathInput)))
                .build();
        assertLoadModel(configPathOutput, opts, true);
    }

    @Test
    void load_model_with_model_config_override_error() throws IOException {
        var configPathInput = "src/test/triton/config_with_model_config_override_error.pbtxt";
        var opts = optsBuilder
                .setModelConfigOverride(Optional.of(Path.of(configPathInput)))
                .build();
        assertLoadModel(null, opts);
    }

    // Creates two models shared between multiple evaluators, verifying the following:
    // 1. Model files are copied to model repository and the model is loaded the first time an evaluator using it is
    // created.
    // 2. The model is unloaded and its files are deleted from model repository when the last evaluator using it is
    // closed.
    @Test
    void model_repository_management_with_reference_counting() throws IOException {
        var opts = optsBuilder.build();

        var modelBaseName1 = "dummy_transformer";
        var modelPath1 = Text.format("src/test/models/onnx/transformer/%s.onnx", modelBaseName1);
        var modelName1 = modelName(modelPath1, opts);

        var modelBaseName2 = "dummy_transformer_mlm";
        var modelPath2 = Text.format("src/test/models/onnx/transformer/%s.onnx", modelBaseName2);
        var modelName2 = modelName(modelPath2, opts);

        var client = createClient();
        var runtime = createRuntime();

        try {
            assertModelRepo(client, 0, Map.of(modelName1, false, modelName2, false));

            var evaluator1 = runtime.evaluatorOf(modelPath1, opts);
            assertModelRepo(client, 1, Map.of(modelName1, true, modelName2, false));

            // Explicit concurrency equal to the automatic default must reuse the same model.
            var explicitOne = new OnnxEvaluatorOptions.Builder(opts)
                    .setConcurrency(1, OnnxEvaluatorOptions.ConcurrencyFactorType.ABSOLUTE)
                    .build();
            var evaluator2 = runtime.evaluatorOf(modelPath1, explicitOne);
            assertModelRepo(client, 1, Map.of(modelName1, true, modelName2, false));

            var evaluator3 = runtime.evaluatorOf(modelPath2, opts);
            assertModelRepo(client, 2, Map.of(modelName1, true, modelName2, true));

            var evaluator4 = runtime.evaluatorOf(modelPath2, opts);
            assertModelRepo(client, 2, Map.of(modelName1, true, modelName2, true));

            evaluator1.close();
            assertModelRepo(client, 2, Map.of(modelName1, true, modelName2, true));

            evaluator2.close();
            assertModelRepo(client, 1, Map.of(modelName1, false, modelName2, true));

            evaluator3.close();
            assertModelRepo(client, 1, Map.of(modelName1, false, modelName2, true));

            evaluator4.close();
            assertModelRepo(client, 0, Map.of(modelName1, false, modelName2, false));

            var evaluator5 = runtime.evaluatorOf(modelPath1, opts);
            assertModelRepo(client, 1, Map.of(modelName1, true, modelName2, false));

            var evaluator6 = runtime.evaluatorOf(modelPath2, opts);
            assertModelRepo(client, 2, Map.of(modelName1, true, modelName2, true));

            evaluator5.close();
            assertModelRepo(client, 1, Map.of(modelName1, false, modelName2, true));

            evaluator6.close();
            assertModelRepo(client, 0, Map.of(modelName1, false, modelName2, false));
        } finally {
            runtime.deconstruct();
            client.close();
        }
    }

    @Test
    void restore_requested_model_when_it_was_removed_independently() {
        var opts = optsBuilder.build();
        var modelName = modelName(MODEL_PATH, opts);
        var client = createClient();
        var runtime = createRuntime();

        try {
            var firstEvaluator = runtime.evaluatorOf(MODEL_PATH, opts);
            assertModelRepo(client, 1, Map.of(modelName, true));

            // Simulate an external unload and repository cleanup while Vespa still references the model.
            client.unloadUntilModelNotReady(modelName);
            assertTrue(IOUtils.recursiveDeleteDir(
                    tritonContainer.getModelRepositoryPath().resolve(modelName).toFile()));
            assertModelRepo(client, 0, Map.of(modelName, false));

            var secondEvaluator = runtime.evaluatorOf(MODEL_PATH, opts);
            assertModelRepo(client, 1, Map.of(modelName, true));

            secondEvaluator.close();
            firstEvaluator.close();
        } finally {
            runtime.deconstruct();
            client.close();
        }
    }

    @Test
    void clean_model_repository_when_runtime_is_created() {
        var opts = optsBuilder.build();
        var modelName = modelName(MODEL_PATH, opts);

        var client = createClient();
        var runtime = createRuntime();

        try {
            var evaluator = runtime.evaluatorOf(MODEL_PATH, opts);
            try {
                assertModelRepo(client, 1, Map.of(modelName, true));

                // A runtime is only constructed at container start, so leftovers are from a previous run.
                var newRuntime = createRuntime();
                try {
                    assertModelRepo(client, 0, Map.of(modelName, false));
                } finally {
                    newRuntime.deconstruct();
                }
            } finally {
                evaluator.close();
            }
        } finally {
            runtime.deconstruct();
            client.close();
        }
    }

    private TritonOnnxClient createClient() {
        return new TritonOnnxClient(testConfig(TritonConfig.ModelControlMode.EXPLICIT, false));
    }

    private TritonOnnxRuntime createRuntime() {
        return createRuntime(false);
    }

    private TritonOnnxRuntime createRuntime(boolean shareOnnxSessionBetweenInstances) {
        return new TritonOnnxRuntime(testConfig(TritonConfig.ModelControlMode.EXPLICIT,
                                                shareOnnxSessionBetweenInstances));
    }

    private static TritonConfig testConfig(TritonConfig.ModelControlMode.Enum mode, boolean shareOnnxSessionBetweenInstances) {
        return new TritonConfig.Builder()
                .target(tritonContainer.getGrpcEndpoint())
                .modelControlMode(mode)
                .modelRepositoryPath(tritonContainer.getModelRepositoryPath().toString())
                .shareOnnxSessionBetweenInstances(shareOnnxSessionBetweenInstances)
                .build();
    }

    private void assertModelRepo(TritonOnnxClient client, int numFiles, Map<String, Boolean> modelReady) {
        assertRepoFileCount(tritonContainer, numFiles);
        modelReady.forEach((modelName, isReady) -> assertEquals(isReady, client.isModelReady(modelName)));
    }

    private static void assertRepoFileCount(TritonServerContainer container, int expected) {
        var repoFiles = container.getModelRepositoryPath().toFile().list();
        assertNotNull(repoFiles);
        assertEquals(expected, repoFiles.length);
    }

    // The model name of a runtime with separate sessions and no known GPU
    private static String modelName(String modelPath, OnnxEvaluatorOptions opts) {
        var resolvedOpts = TritonOnnxRuntime.resolveOptions(opts, false, Set.of());
        return TritonOnnxRuntime.generateModelName(modelPath, resolvedOpts, false);
    }

    // expectedConfigPath == null means we expect an error during model loading
    private void assertLoadModel(String expectedConfigPath, OnnxEvaluatorOptions evalOpts) throws IOException {
        assertLoadModel(expectedConfigPath, evalOpts, false);
    }

    private void assertLoadModel(String expectedConfigPath, OnnxEvaluatorOptions evalOpts, boolean shareSession)
            throws IOException {
        var modelBaseName = "dummy_transformer";
        var testModelFilePath = Text.format("src/test/models/onnx/transformer/%s.onnx", modelBaseName);
        var resolvedOpts = evalOpts.modelConfigOverride().isPresent()
                ? evalOpts : TritonOnnxRuntime.resolveOptions(evalOpts, shareSession, Set.of());
        var modelName = TritonOnnxRuntime.generateModelName(testModelFilePath, resolvedOpts, shareSession);
        var modelFilePath = Text.format("%s/1/model.onnx", modelName);
        var modelConfigPath = Text.format("%s/config.pbtxt", modelName);
        var runtime = createRuntime(shareSession);

        try {
            ThrowingSupplier<OnnxEvaluator> evaluatorSupplier = () -> runtime.evaluatorOf(testModelFilePath, evalOpts);
            if (expectedConfigPath == null) {
                assertThrows(IllegalArgumentException.class, evaluatorSupplier::get);
                return;
            }
            var evaluator = assertDoesNotThrow(evaluatorSupplier);
            assertNotNull(evaluator);

            var configFile = tritonContainer.getModelRepositoryPath().resolve(modelConfigPath);
            var expectedFilePermissions = PosixFilePermissions.fromString("rw-r--r--");
            assertEquals(expectedFilePermissions, Files.getPosixFilePermissions(configFile));
            var actualConfig = Files.readString(Paths.get(configFile.toString()));
            var expectedConfig = Files.readString(Paths.get(expectedConfigPath));
            assertEqualConfigs(expectedConfig, actualConfig);

            var modelFile = tritonContainer.getModelRepositoryPath().resolve(modelFilePath);
            assertEquals(expectedFilePermissions, Files.getPosixFilePermissions(modelFile));

            // The stock image ignores the parameter, so only the generated config can be checked there.
            if (shareSession && tritonContainer.supportsSessionSharing()) {
                assertTrue(tritonContainer.getLogs().contains("Reusing session for instance: " + modelName),
                           "Triton did not reuse the session between instances of " + modelName);
            }

            evaluator.close();
        } finally {
            runtime.deconstruct();
        }
    }

    // Removes hash from model name before comparing configs.
    // This is to avoid updating hash in all test config files every time options are changed.
    private void assertEqualConfigs(String expectedConfig, String actualConfig) {
        var regex = "(name:\\s*\"\\w+)_[a-f0-9]+\"";
        var normalizedExpected = expectedConfig.replaceFirst(regex, "$1\"");
        var normalizedActual = actualConfig.replaceFirst(regex, "$1\"");
        assertEquals(normalizedExpected, normalizedActual);
    }

    // Simulates deploying a faulty model followed by a working one.
    // The faulty model must fail with an exception, not kill the process, and leave no state behind.
    // The working model must load even though Triton still tracks the unrelated failed model.
    @Test
    void recover_when_faulty_model_is_replaced_by_working_model() throws IOException {
        var faultyModelPath = Files.createTempFile("faulty_model", ".onnx");
        Files.writeString(faultyModelPath, "this is not a valid onnx model");
        var runtime = createRuntime();
        var opts = optsBuilder.build();

        try {
            var exception = assertThrows(
                    TritonOnnxClient.TritonException.class,
                    () -> runtime.evaluatorOf(faultyModelPath.toString(), opts));
            var message = Exceptions.toMessageString(exception);
            assertTrue(message.contains("faulty_model"), message);
            assertTrue(message.contains("failed"), message);
            assertTrue(message.contains("Protobuf parsing failed"), message);

            // The failed model must not leave files behind in the model repository.
            assertRepoFileCount(tritonContainer, 0);

            var evaluator = assertDoesNotThrow(() -> runtime.evaluatorOf(MODEL_PATH, opts));
            assertNotNull(evaluator);
            evaluator.close();
        } finally {
            runtime.deconstruct();
            Files.deleteIfExists(faultyModelPath);
        }
    }

    // Hammers concurrent create and close of the same model from multiple threads,
    // exercising the deadlock and adoption races between evaluatorOf and evaluator close.
    // A deadlock fails the test through the future timeouts.
    @Test
    void concurrent_create_and_close_of_the_same_model() throws Exception {
        var opts = optsBuilder.build();
        var modelName = modelName(MODEL_PATH, opts);
        var client = createClient();
        var runtime = createRuntime();
        var threads = 4;
        var iterations = 25;
        var executor = Executors.newFixedThreadPool(threads);

        try {
            try {
                var futures = new ArrayList<Future<?>>();

                for (int thread = 0; thread < threads; thread++) {
                    futures.add(executor.submit(() -> {
                        for (int i = 0; i < iterations; i++) {
                            runtime.evaluatorOf(MODEL_PATH, opts).close();
                        }
                        return null;
                    }));
                }

                for (var future : futures) {
                    future.get(120, TimeUnit.SECONDS);
                }
            } finally {
                executor.shutdownNow();
                runtime.deconstruct();
            }

            assertModelRepo(client, 0, Map.of(modelName, false));
        } finally {
            client.close();
        }
    }

}
