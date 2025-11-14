// Copyright Vespa.ai. Licensed under the Apache License, Version 2.0.
package ai.vespa.triton;

import ai.vespa.llm.clients.TritonConfig;
import ai.vespa.modelintegration.evaluator.OnnxEvaluator;
import ai.vespa.modelintegration.evaluator.OnnxEvaluatorOptions;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.function.ThrowingSupplier;

/**
 * @author bjorncs
 * @author glebashnik
 */
@EnabledIfSystemProperty(named = "VESPA_USE_TRITON", matches = "true")
@ExtendWith(ContainerEnvironmentAvailableCondition.class)
class TritonOnnxRuntimeTest {

    private static TritonServerContainer tritonContainer;
    private final OnnxEvaluatorOptions.Builder optsBuilder =  new OnnxEvaluatorOptions.Builder(8);

    @BeforeAll
    static void setupTriton() throws IOException {
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
        var opts = optsBuilder.setThreads(8, 16).build();
        assertLoadModel("src/test/triton/config_with_threads.pbtxt", opts);
    }

    @Test
    void load_model_with_batching() throws IOException {
        var opts = optsBuilder.setBatchingMaxSize(10).setBatchingMaxDelay(Duration.ofMillis(100)).build();
        assertLoadModel("src/test/triton/config_with_batching.pbtxt", opts);
    }

    @Test
    void load_model_with_absolute_concurrency() throws IOException {
        var opts = optsBuilder.setConcurrency(2, OnnxEvaluatorOptions.ConcurrencyFactorType.ABSOLUTE).build();
        assertLoadModel("src/test/triton/config_with_absolute_concurrency.pbtxt", opts);
    }

    @Test
    void load_model_with_relative_concurrency() throws IOException {
        var opts = optsBuilder.setConcurrency(1.5, OnnxEvaluatorOptions.ConcurrencyFactorType.RELATIVE).build();
        assertLoadModel("src/test/triton/config_with_relative_concurrency.pbtxt", opts);
    }

    @Test
    void load_model_with_model_config_override() throws IOException {
        var configPathInput = "src/test/triton/config_with_model_config_override_input.pbtxt";
        var configPathOutput = "src/test/triton/config_with_model_config_override_output.pbtxt";
        var opts = optsBuilder.setModelConfigOverride(Optional.of(Path.of(configPathInput))).build();
        assertLoadModel(configPathOutput, opts);
    }

    @Test
    void load_model_with_model_config_override_error() throws IOException {
        var configPathInput = "src/test/triton/config_with_model_config_override_error.pbtxt";
        var opts = optsBuilder.setModelConfigOverride(Optional.of(Path.of(configPathInput))).build();
        assertLoadModel(null, opts);
    }
    
    // expectedConfigPath == null means we expect an error during model loading
    private void assertLoadModel(String expectedConfigPath, OnnxEvaluatorOptions evalOpts) throws IOException {
        var modelBaseName = "dummy_transformer";
        var testModelFilePath = String.format("src/test/models/onnx/transformer/%s.onnx", modelBaseName);
        var modelName = TritonOnnxRuntime.generateModelName(testModelFilePath, evalOpts);
        var modelFilePath = String.format("%s/1/model.onnx", modelName);
        var modelConfigPath = String.format("%s/config.pbtxt", modelName);

        var tritonConfig = new TritonConfig.Builder()
                .target(tritonContainer.getGrpcEndpoint())
                .modelControlMode(TritonConfig.ModelControlMode.EXPLICIT)
                .modelRepositoryPath(tritonContainer.getModelRepositoryPath().toString())
                .build();
        var tritonRuntime = new TritonOnnxRuntime(tritonConfig);

        try {
            ThrowingSupplier<OnnxEvaluator> evaluatorSupplier = () -> tritonRuntime.evaluatorOf(testModelFilePath, evalOpts);
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
            assertEquals(expectedConfig, actualConfig);

            var modelFile = tritonContainer.getModelRepositoryPath().resolve(modelFilePath);
            assertEquals(expectedFilePermissions, Files.getPosixFilePermissions(modelFile));
            evaluator.close();
        } finally {
            tritonRuntime.deconstruct();
        }
    }
}
