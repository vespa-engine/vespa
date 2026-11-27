// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import ai.vespa.llm.clients.TritonConfig;
import com.yahoo.language.process.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests model loading and unloading through TritonModelRepositoryClient against a Triton container.
 *
 * @author bjorncs
 * @author glebashnik
 */
@EnabledIfSystemProperty(named = "VESPA_USE_TRITON", matches = "true")
@ExtendWith(ContainerEnvironmentAvailableCondition.class)
class TritonModelRepositoryClientTest {
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
    void is_model_ready_model() {
        try (var repositoryClient = createRepositoryClient()) {
            var isReadyBeforeLoad = repositoryClient.isModelReady(MODEL_NAME);
            assertFalse(isReadyBeforeLoad);

            repositoryClient.loadModel(MODEL_NAME);
            var isReadyAfterLoad = repositoryClient.isModelReady(MODEL_NAME);
            assertTrue(isReadyAfterLoad);

            repositoryClient.unloadModel(MODEL_NAME);
            var isReadyAfterUnload = repositoryClient.isModelReady(MODEL_NAME);
            assertFalse(isReadyAfterUnload);
        }
    }

    @Test
    void loadUntilModelReady_succeeds_when_load_succeeds_not_immediately() {
        var modelName = "test-model";

        try (var repositoryClient = createRepositoryClient()) {
            var client = spy(repositoryClient);

            when(client.isModelReady(modelName)).thenReturn(false).thenReturn(true);
            var exception = new TritonException("Load failed");
            doThrow(exception).doThrow(exception).doNothing().when(client).loadModel(modelName);

            client.loadUntilModelReady(modelName);

            verify(client, times(2)).isModelReady(modelName);
            verify(client, times(3)).loadModel(modelName);
        }
    }

    @Test
    void loadUntilModelReady_succeeds_when_not_ready_immediately() {
        var modelName = "test-model";

        try (var repositoryClient = createRepositoryClient()) {
            var client = spy(repositoryClient);

            when(client.isModelReady(modelName))
                    .thenReturn(false)
                    .thenReturn(false)
                    .thenReturn(true);
            doNothing().when(client).loadModel(modelName);

            client.loadUntilModelReady(modelName);

            verify(client, times(3)).isModelReady(modelName);
            verify(client, times(1)).loadModel(modelName);
        }
    }

    // The messages are the ones Triton 26.09 returns.
    @Test
    void loadUntilModelReady_fails_without_retrying_when_triton_rejects_the_config() {
        var rejections = List.of(
                "INVALID_ARGUMENT: load failed for model 'test-model': version 1 is at UNAVAILABLE state: Invalid argument: " +
                "autofill failed for model 'test-model': model does not support batching while non-zero max_batch_size is specified;",
                "INVALID_ARGUMENT: load failed for model 'test-model': version 1 is at UNAVAILABLE state: Invalid argument: " +
                "unable to load model 'test-model', configuration expects datatype TYPE_FP16 for input 'x', model provides TYPE_FP32;");
        for (var rejection : rejections) {
            try (var repositoryClient = createRepositoryClient()) {
                var client = spy(repositoryClient);
                doThrow(new TritonException("Failed to load model", new RuntimeException(rejection)))
                        .when(client).loadModel("test-model");

                var thrown = assertThrows(TritonException.class, () -> client.loadUntilModelReady("test-model"));

                assertTrue(TritonModelRepositoryClient.isConfigRejection(thrown), rejection);
                verify(client, times(1)).loadModel("test-model");
            }
        }
    }

    // Triton reports a model file that is not completely copied yet as an internal error.
    @Test
    void loadUntilModelReady_retries_when_the_model_file_is_incomplete() {
        try (var repositoryClient = createRepositoryClient()) {
            var client = spy(repositoryClient);
            var incomplete = new TritonException("Failed to load model", new RuntimeException(
                    "INVALID_ARGUMENT: load failed for model 'test-model': version 1 is at UNAVAILABLE state: Internal: " +
                    "onnx runtime error 7: Load model from /models/test-model/1/model.onnx failed:Protobuf parsing failed.;"));
            doThrow(incomplete).doNothing().when(client).loadModel("test-model");
            doReturn(false, true).when(client).isModelReady("test-model");

            client.loadUntilModelReady("test-model");

            assertFalse(TritonModelRepositoryClient.isConfigRejection(incomplete));
            verify(client, times(2)).loadModel("test-model");
        }
    }

    @Test
    void loadUntilModelReady_fails_when_load_always_fails() {
        var modelName = "test-model";

        try (var repositoryClient = createRepositoryClient()) {
            var client = spy(repositoryClient);

            // Will always throw because this model doesn't exist
            assertThrows(TritonException.class, () -> client.loadUntilModelReady(modelName));

            verify(client, times(1)).isModelReady(modelName);
            verify(client, times(5)).loadModel(modelName);
        }
    }

    @Test
    void loadUntilModelReady_fails_when_never_ready() {
        var modelName = "test-model";

        try (var repositoryClient = createRepositoryClient()) {
            var client = spy(repositoryClient);

            when(client.isModelReady(modelName)).thenReturn(false);
            doNothing().when(client).loadModel(modelName);

            assertThrows(TritonException.class, () -> client.loadUntilModelReady(modelName));

            verify(client, times(6)).isModelReady(modelName);
            verify(client, times(1)).loadModel(modelName);
        }
    }

    @Test
    void unloadUntilModelNotReady_succeeds_when_unload_succeeds_not_immediately() {
        var modelName = "test-model";

        try (var repositoryClient = createRepositoryClient()) {
            var client = spy(repositoryClient);

            doReturn(
                    TritonModelRepositoryClient.ModelActivity.LOADED_OR_LOADING,
                    TritonModelRepositoryClient.ModelActivity.LOADED_OR_LOADING,
                    TritonModelRepositoryClient.ModelActivity.LOADED_OR_LOADING,
                    TritonModelRepositoryClient.ModelActivity.INACTIVE)
                    .when(client).modelActivity(modelName);
            var exception = new TritonException("Unload failed");
            doThrow(exception).doThrow(exception).doNothing().when(client).unloadModel(modelName);

            client.unloadUntilModelNotReady(modelName);

            verify(client, times(4)).modelActivity(modelName);
            verify(client, times(3)).unloadModel(modelName);
        }
    }

    @Test
    void unloadUntilModelNotReady_succeeds_when_not_ready_immediately() {
        var modelName = "test-model";

        try (var repositoryClient = createRepositoryClient()) {
            var client = spy(repositoryClient);

            doReturn(
                    TritonModelRepositoryClient.ModelActivity.LOADED_OR_LOADING,
                    TritonModelRepositoryClient.ModelActivity.LOADED_OR_LOADING,
                    TritonModelRepositoryClient.ModelActivity.LOADED_OR_LOADING,
                    TritonModelRepositoryClient.ModelActivity.INACTIVE)
                    .when(client).modelActivity(modelName);
            doNothing().when(client).unloadModel(modelName);

            client.unloadUntilModelNotReady(modelName);

            verify(client, times(4)).modelActivity(modelName);
            verify(client, times(1)).unloadModel(modelName);
        }
    }

    @Test
    void unloadUntilModelNotReady_waits_for_an_already_unloading_model() {
        var modelName = "test-model";

        try (var repositoryClient = createRepositoryClient()) {
            var client = spy(repositoryClient);

            doReturn(
                    TritonModelRepositoryClient.ModelActivity.UNLOADING,
                    TritonModelRepositoryClient.ModelActivity.INACTIVE)
                    .when(client).modelActivity(modelName);

            client.unloadUntilModelNotReady(modelName);

            verify(client, times(2)).modelActivity(modelName);
            verify(client, times(0)).unloadModel(modelName);
        }
    }

    @Test
    void unloadUntilModelNotReady_fails_when_unload_always_fails() {
        var modelName = "test-model";

        try (var repositoryClient = createRepositoryClient()) {
            var client = spy(repositoryClient);

            doReturn(TritonModelRepositoryClient.ModelActivity.LOADED_OR_LOADING)
                    .when(client).modelActivity(modelName);
            var exception = new TritonException("Unload failed");
            doThrow(exception).when(client).unloadModel(modelName);

            assertThrows(TritonException.class, () -> client.unloadUntilModelNotReady(modelName));

            verify(client, times(5)).modelActivity(modelName);
            verify(client, times(5)).unloadModel(modelName);
        }
    }

    @Test
    void unloadUntilModelNotReady_fails_when_never_not_ready() {
        var modelName = "test-model";

        try (var repositoryClient = createRepositoryClient()) {
            var client = spy(repositoryClient);

            doReturn(TritonModelRepositoryClient.ModelActivity.LOADED_OR_LOADING)
                    .when(client).modelActivity(modelName);
            doNothing().when(client).unloadModel(modelName);

            assertThrows(TritonException.class, () -> client.unloadUntilModelNotReady(modelName));

            verify(client, times(6)).modelActivity(modelName);
            verify(client, times(1)).unloadModel(modelName);
        }
    }

    private static TritonModelRepositoryClient createRepositoryClient() {
        return new TritonModelRepositoryClient(new TritonConfig.Builder()
                .target(tritonContainer.getGrpcEndpoint())
                .build());
    }
}
