// Copyright Vespa.ai. Licensed under the Apache License, Version 2.0.
package ai.vespa.triton;

import ai.vespa.modelintegration.evaluator.OnnxEvaluator;
import ai.vespa.modelintegration.evaluator.OnnxEvaluatorOptions;
import com.yahoo.container.protect.ProcessTerminator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.function.ThrowingSupplier;

/**
 * @author bjorncs
 * @author glebashnik
 */
@EnabledIfSystemProperty(named = "VESPA_USE_TRITON", matches = "true")
@ExtendWith(ContainerEnvironmentAvailableCondition.class)
class TritonOnnxRuntimeTest {

    private static TritonServerContainer tritonContainer;
    private final OnnxEvaluatorOptions.Builder optsBuilder = new OnnxEvaluatorOptions.Builder(8);

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
        // TritonOnnxRuntime ignores intraOpThreadsFactor
        var opts = optsBuilder.setThreadsFromFactors(8, 16).build();
        assertLoadModel("src/test/triton/config_with_threads.pbtxt", opts);
    }

    @Test
    void load_model_with_execution_mode() throws IOException {
        var opts = optsBuilder.setExecutionMode(OnnxEvaluatorOptions.ExecutionMode.PARALLEL).build();
        assertLoadModel("src/test/triton/config_with_execution_mode.pbtxt", opts);
    }

    @Test
    void load_model_with_batching() throws IOException {
        var opts = optsBuilder
                .setBatchingMaxSize(10)
                .setBatchingMaxDelay(Duration.ofMillis(100))
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
    void load_model_with_model_config_override() throws IOException {
        var configPathInput = "src/test/triton/config_with_model_config_override_input.pbtxt";
        var configPathOutput = "src/test/triton/config_with_model_config_override_output.pbtxt";
        var opts = optsBuilder
                .setModelConfigOverride(Optional.of(Path.of(configPathInput)))
                .build();
        assertLoadModel(configPathOutput, opts);
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
        var modelPath1 = "src/test/models/onnx/transformer/%s.onnx".formatted(modelBaseName1);
        var modelName1 = TritonOnnxRuntime.generateModelName(modelPath1, opts);

        var modelBaseName2 = "dummy_transformer_mlm";
        var modelPath2 = "src/test/models/onnx/transformer/%s.onnx".formatted(modelBaseName2);
        var modelName2 = TritonOnnxRuntime.generateModelName(modelPath2, opts);

        var client = createClient();
        var runtime = createRuntime();

        try {
            assertModelRepo(client, 0, Map.of(modelName1, false, modelName2, false));

            var evaluator1 = runtime.evaluatorOf(modelPath1, opts);
            assertModelRepo(client, 1, Map.of(modelName1, true, modelName2, false));

            var evaluator2 = runtime.evaluatorOf(modelPath1, opts);
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
        }
    }

    @Test
    void clean_model_repository_when_runtime_is_created() throws IOException {
        var opts = optsBuilder.build();

        var modelBaseName = "dummy_transformer";
        var modelPath = "src/test/models/onnx/transformer/%s.onnx".formatted(modelBaseName);
        var modelName = TritonOnnxRuntime.generateModelName(modelPath, opts);

        var client = createClient();
        var runtime = createRuntime();

        runtime.evaluatorOf(modelPath, opts);
        assertModelRepo(client, 1, Map.of(modelName, true));

        createRuntime();
        assertModelRepo(client, 0, Map.of(modelName, false));
    }

    private TritonOnnxClient createClient() {
        var tritonConfig = new TritonConfig.Builder()
                .grpcEndpoint(tritonContainer.getGrpcEndpoint())
                .modelControlMode(TritonConfig.ModelControlMode.EXPLICIT)
                .modelRepository(tritonContainer.getModelRepositoryPath().toString())
                .build();
        return new TritonOnnxClient(tritonConfig);
    }

    private TritonOnnxRuntime createRuntime() {
        var tritonConfig = new TritonConfig.Builder()
                .grpcEndpoint(tritonContainer.getGrpcEndpoint())
                .modelControlMode(TritonConfig.ModelControlMode.EXPLICIT)
                .modelRepository(tritonContainer.getModelRepositoryPath().toString())
                .build();
        return new TritonOnnxRuntime(tritonConfig);
    }

    private void assertModelRepo(TritonOnnxClient client, int numFiles, Map<String, Boolean> modelReady) {
        var repoFiles = tritonContainer.getModelRepositoryPath().toFile().list();
        assertNotNull(repoFiles);
        assertEquals(numFiles, repoFiles.length);
        modelReady.forEach((modelName, isReady) -> assertEquals(isReady, client.isModelReady(modelName)));
    }

    // expectedConfigPath == null means we expect an error during model loading
    private void assertLoadModel(String expectedConfigPath, OnnxEvaluatorOptions evalOpts) throws IOException {
        var modelBaseName = "dummy_transformer";
        var testModelFilePath = String.format("src/test/models/onnx/transformer/%s.onnx", modelBaseName);
        var modelName = TritonOnnxRuntime.generateModelName(testModelFilePath, evalOpts);
        var modelFilePath = String.format("%s/1/model.onnx", modelName);
        var modelConfigPath = String.format("%s/config.pbtxt", modelName);
        var runtime = createRuntime();

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
            evaluator.close();
        } finally {
            runtime.deconstruct();
        }
    }

    // Removes hash from model name before comparing configs.
    // This is to avoid updating hash in all test config files every time options are changed.
    private void assertEqualConfigs(String expectedConfig, String actualConfig) {
        var regex = "(name:\\s*\"\\w+)_[a-f0-9]{16}\"";
        var normalizedExpected = expectedConfig.replaceFirst(regex, "$1\"");
        var normalizedActual = actualConfig.replaceFirst(regex, "$1\"");
        assertEquals(normalizedExpected, normalizedActual);
    }

    @Test
    void die_when_triton_server_is_unreachable() {
        var config = new TritonConfig.Builder()
                .grpcEndpoint("localhost:9999")  // Non-existent server
                .build();
        var mockTerminator = new MockProcessTerminator();

        var runtime = new TritonOnnxRuntime(config, mockTerminator);
        var modelPath = "src/test/models/onnx/transformer/dummy_transformer.onnx";
        var opts = optsBuilder.build();

        assertThrows(MockProcessTerminationException.class, () -> runtime.evaluatorOf(modelPath, opts));
        assertEquals(1, mockTerminator.dieRequests);
        assertTrue(mockTerminator.lastMessage.contains("can't be reached"));
        assertTrue(mockTerminator.lastMessage.contains("localhost:9999"));
    }

    @Test
    void die_when_triton_server_is_unhealthy() {
        var config = new TritonConfig.Builder()
                .grpcEndpoint(tritonContainer.getGrpcEndpoint())
                .build();
        
        var mockClient = new TritonOnnxClient(config) {
            @Override
            public boolean isHealthy() {
                return false;
            }
        };
        
        var mockTerminator = new MockProcessTerminator();
        var runtime = new TritonOnnxRuntime(config, mockClient, mockTerminator);
        var modelPath = "src/test/models/onnx/transformer/dummy_transformer.onnx";
        var opts = optsBuilder.build();

        assertThrows(MockProcessTerminationException.class, () -> runtime.evaluatorOf(modelPath, opts));
        assertEquals(1, mockTerminator.dieRequests);
        assertTrue(mockTerminator.lastMessage.contains("not healthy"));
        assertTrue(mockTerminator.lastMessage.contains(tritonContainer.getGrpcEndpoint()));
    }

    @Test
    void not_die_when_triton_server_is_healthy() {
        var config = new TritonConfig.Builder()
                .grpcEndpoint(tritonContainer.getGrpcEndpoint())
                .modelControlMode(TritonConfig.ModelControlMode.EXPLICIT)
                .modelRepository(tritonContainer.getModelRepositoryPath().toString())
                .build();

        var mockTerminator = new MockProcessTerminator();
        var runtime = new TritonOnnxRuntime(config, mockTerminator);

        var modelPath = "src/test/models/onnx/transformer/dummy_transformer.onnx";
        var opts = optsBuilder.build();

        // Verify that evaluatorOf succeeds when server is healthy
        var evaluator = assertDoesNotThrow(() -> runtime.evaluatorOf(modelPath, opts));
        assertNotNull(evaluator);
        assertEquals(0, mockTerminator.dieRequests);

        evaluator.close();
        runtime.deconstruct();
    }

    private static class MockProcessTerminator extends ProcessTerminator {
        public int dieRequests = 0;
        public String lastMessage = null;

        @Override
        public void logAndDie(String message, boolean dumpThreads) {
            dieRequests++;
            lastMessage = message;
            throw new MockProcessTerminationException("Simulated process termination: " + message);
        }

        @Override
        public void logAndDie(String message) {
            logAndDie(message, false);
        }
    }

    private static class MockProcessTerminationException extends RuntimeException {
        public MockProcessTerminationException(String message) {
            super(message);
        }
    }
}
