// Copyright Vespa.ai. Licensed under the Apache License, Version 2.0.
package ai.vespa.triton;

import ai.vespa.llm.clients.TritonConfig;
import ai.vespa.modelintegration.evaluator.OnnxEvaluatorOptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author bjorncs
 */
@EnabledIfSystemProperty(named = "VESPA_USE_TRITON", matches = "true")
@ExtendWith(ContainerEnvironmentAvailableCondition.class)
class TritonOnnxRuntimeTest {

    private static TritonServerContainer tritonContainer;

    @BeforeAll
    static void setupTriton() throws IOException {
        tritonContainer = new TritonServerContainer();
        tritonContainer.start();
    }

    @Test
    void loads_model_with_config() throws IOException {
        var runtime = new TritonOnnxRuntime(
                new TritonConfig.Builder()
                        .target(tritonContainer.getGrpcEndpoint())
                        .modelControlMode(TritonConfig.ModelControlMode.EXPLICIT)
                        .modelRepositoryPath(tritonContainer.getModelRepositoryPath().toString())
                        .build());

        var modelBaseName = "dummy_transformer";
        var modelHash = "ed9202701f4a70a9";
        var testModelFilePath = String.format("src/test/models/onnx/transformer/%s.onnx", modelBaseName);
        var testModelConfigPath = "src/test/triton/config.pbtxt";
        var modelName = String.format("%s_%s", modelBaseName, modelHash);
        var modelFilePath = String.format("%s/1/model.onnx", modelName);
        var modelConfigPath = String.format("%s/config.pbtxt",modelName);
        
        try {
            var evaluator = assertDoesNotThrow(() -> runtime.evaluatorOf(
                    testModelFilePath,
                    new OnnxEvaluatorOptions.Builder()
                            .setExecutionMode(OnnxEvaluatorOptions.ExecutionMode.PARALLEL)
                            .setThreads(5, 5)
                            .build()));
            assertNotNull(evaluator);
            
            var configFile = tritonContainer.getModelRepositoryPath().resolve(modelConfigPath);
            var expectedFilePermissions = PosixFilePermissions.fromString("rw-r--r--");
            assertEquals(expectedFilePermissions, Files.getPosixFilePermissions(configFile));
            var actualConfig = Files.readString(Paths.get(configFile.toString()));
            var expectedConfig = Files.readString(Paths.get(testModelConfigPath));
            assertEquals(expectedConfig, actualConfig);

            var modelFile = tritonContainer.getModelRepositoryPath().resolve(modelFilePath);
            assertEquals(expectedFilePermissions, Files.getPosixFilePermissions(modelFile));
            evaluator.close();
        } finally {
            runtime.deconstruct();
        }
    }
}
