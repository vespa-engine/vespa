// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import ai.vespa.llm.clients.TritonConfig;
import com.yahoo.language.process.TimeoutException;
import com.yahoo.tensor.Tensor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bjorncs
 * @author glebashnik
 */
@EnabledIfSystemProperty(named = "VESPA_USE_TRITON", matches = "true")
@ExtendWith(ContainerEnvironmentAvailableCondition.class)
class TritonOnnxClientTest {
    private static final String MODEL_NAME = "dummy_transformer";

    private static TritonServerContainer tritonContainer;

    @BeforeAll
    static void launchTritonServer() throws IOException {
        tritonContainer = new TritonServerContainer();
        tritonContainer.addModel(MODEL_NAME, Path.of("src/test/models/onnx/transformer/" + MODEL_NAME + ".onnx"));
        tritonContainer.start();
    }

    @AfterEach
    void unload_model() {
        try (var repositoryClient = createRepositoryClient()) {
            repositoryClient.unloadModel(MODEL_NAME);
        }
    }

    @Test
    void loads_model_and_does_inference() {
        try (var tritonClient = createTritonClient(); var repositoryClient = createRepositoryClient()) {
            repositoryClient.loadModel(MODEL_NAME);

            var inputIds = Tensor.from(
                    "tensor<float>(d0[1],d1[5]):{{d0:0,d1:0}:1.0, {d0:0,d1:1}:2.0, {d0:0,d1:2}:3.0, {d0:0,d1:3}:4.0,"
                            + " {d0:0,d1:4}:5.0}");
            var attentionMask = Tensor.from(
                    "tensor<float>(d0[1],d1[5]):{{d0:0,d1:0}:1.0, {d0:0,d1:1}:1.0, {d0:0,d1:2}:1.0, {d0:0,d1:3}:1.0,"
                            + " {d0:0,d1:4}:1.0}");
            var tokenTypeIds = Tensor.from(
                    "tensor<float>(d0[1],d1[5]):{{d0:0,d1:0}:0.0, {d0:0,d1:1}:0.0, {d0:0,d1:2}:0.0, {d0:0,d1:3}:0.0,"
                            + " {d0:0,d1:4}:0.0}");

            var inputs = Map.of(
                    "input_ids", inputIds,
                    "attention_mask", attentionMask,
                    "token_type_ids", tokenTypeIds);
            var metadata = tritonClient.getModelMetadata(MODEL_NAME);
            var output = tritonClient.evaluate(MODEL_NAME, metadata, inputs, "output_0");
            var expectedOutput = Tensor.from(
                    "tensor<float>(d0[1],d1[5],d2[16]):[[[0.4804825, 0.40018916, 0.7545914, 0.042122524, 1.7800103,"
                            + " 0.5808189, -0.19873382, 0.771073, -0.89801484, -0.6752946, -0.041897558, -2.3824935,"
                            + " 0.49392065, 0.012589367, 0.68263936, -1.8020033], [-0.33929396, -0.2736117, 0.06295472,"
                            + " 1.274656, 0.18820663, 0.7790934, -1.1644478, 1.0001067, 0.09716945, 0.10663589,"
                            + " -1.6964301, 2.2415364, -0.8511695, -0.083222374, -1.573108, 0.23092414], [-0.7304796,"
                            + " -0.015563937, -0.92283815, -0.77366585, -0.58798325, -1.215584, -0.79734313, 1.7975526,"
                            + " 0.20870286, 1.746855, 0.3003435, -0.887233, 0.76792, 0.4185295, -0.948748, 1.6395345],"
                            + " [-1.5298998, -1.8807766, 0.9148114, 0.23836139, -0.8136509, 0.34324786, 1.1250327,"
                            + " 0.5424437, 0.7097117, 0.5137732, 1.1006811, 1.3292117, 0.12168954, -0.44888124,"
                            + " -1.5567778, -0.7089778], [-0.97006357, -2.2981524, 0.9113274, 1.1748145, -1.2200266,"
                            + " -0.48628148, 0.10357287, 0.8698752, -0.39116782, 1.006429, 0.5442105, 0.29821596,"
                            + " 1.142777, -0.58772075, -1.0151181, 0.9173087]]]");
            assertEquals(output, expectedOutput);
        }
    }

    @Test
    void evaluate_throws_timeout_exception_for_pre_expired_deadline() {
        try (var tritonClient = createTritonClient(); var repositoryClient = createRepositoryClient()) {
            repositoryClient.loadModel(MODEL_NAME);
            var metadata = tritonClient.getModelMetadata(MODEL_NAME);
            var inputs = Map.of(
                    "input_ids",      Tensor.from("tensor<float>(d0[1],d1[1]):[1.0]"),
                    "attention_mask", Tensor.from("tensor<float>(d0[1],d1[1]):[1.0]"),
                    "token_type_ids", Tensor.from("tensor<float>(d0[1],d1[1]):[0.0]"));

            var ex = assertThrows(TimeoutException.class,
                    () -> tritonClient.evaluate(MODEL_NAME, metadata, inputs, "output_0", Optional.of(Instant.now())));
            assertTrue(ex.getMessage().contains("deadline exceeded") || ex.getMessage().contains("Request deadline"),
                    "unexpected message: " + ex.getMessage());
        }
    }

    @Test
    void evaluate_throws_timeout_exception_when_deadline_fires_during_rpc() {
        try (var tritonClient = createTritonClient(); var repositoryClient = createRepositoryClient()) {
            repositoryClient.loadModel(MODEL_NAME);
            var metadata = tritonClient.getModelMetadata(MODEL_NAME);
            var inputs = Map.of(
                    "input_ids",      Tensor.from("tensor<float>(d0[1],d1[5]):[1.0, 2.0, 3.0, 4.0, 5.0]"),
                    "attention_mask", Tensor.from("tensor<float>(d0[1],d1[5]):[1.0, 1.0, 1.0, 1.0, 1.0]"),
                    "token_type_ids", Tensor.from("tensor<float>(d0[1],d1[5]):[0.0, 0.0, 0.0, 0.0, 0.0]"));

            // Pausing the container guarantees that the server cannot answer, and we get a timeout.
            var dockerClient = tritonContainer.getDockerClient();
            dockerClient.pauseContainerCmd(tritonContainer.getContainerId()).exec();

            try {
                assertThrows(TimeoutException.class,
                        () -> tritonClient.evaluate(MODEL_NAME, metadata, inputs, "output_0", Optional.of(Instant.now().plusMillis(200))));
            } finally {
                dockerClient.unpauseContainerCmd(tritonContainer.getContainerId()).exec();
            }
        }
    }

    @Test
    void batching_rejection_is_recognized() {
        assertTrue(TritonOnnxClient.isBatchingRejection(new TritonException("Failed to load model", new RuntimeException(
                "INVALID_ARGUMENT: load failed for model 'test-model': version 1 is at UNAVAILABLE state: Invalid argument: " +
                "autofill failed for model 'test-model': model does not support batching while non-zero max_batch_size is specified;"))));
    }

    private static TritonOnnxClient createTritonClient() {
        return new TritonOnnxClient(new TritonConfig.Builder()
                .target(tritonContainer.getGrpcEndpoint())
                .build());
    }

    private static TritonModelRepositoryClient createRepositoryClient() {
        return new TritonModelRepositoryClient(new TritonConfig.Builder()
                .target(tritonContainer.getGrpcEndpoint())
                .build());
    }
}
