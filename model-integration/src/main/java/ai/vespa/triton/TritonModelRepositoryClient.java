// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import ai.vespa.llm.clients.TritonConfig;
import com.yahoo.api.annotations.Beta;
import com.yahoo.language.process.TimeoutException;
import com.yahoo.yolean.Exceptions;
import inference.GRPCInferenceServiceGrpc;
import inference.GrpcService;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * Manages the models of Triton Inference Server through its gRPC API, whatever their backend: whether a model is
 * ready, and loading and unloading models from its model repository (Triton's model repository extension of KServe).
 * Inference goes through the client of each backend, e.g. {@link TritonOnnxClient}.
 *
 * @author bjorncs
 * @author glebashnik
 */
@Beta
public class TritonModelRepositoryClient implements AutoCloseable {

    private static final int MAX_MODEL_LOAD_ATTEMPTS = 5;
    private static final int MAX_MODEL_LOAD_WAIT_MILLIS = 1000;
    private static final int MAX_MODEL_UNLOAD_ATTEMPTS = 5;
    private static final int MAX_MODEL_UNLOAD_WAIT_MILLIS = 1000;

    // Deadline for status checks.
    private static final Duration STATUS_CHECK_TIMEOUT = Duration.ofSeconds(10);

    // Deadline for unload requests.
    private static final Duration UNLOAD_TIMEOUT = Duration.ofMinutes(1);

    // Deadline for load requests: generous, since loading a large model takes minutes.
    private static final Duration LOAD_TIMEOUT = Duration.ofMinutes(5);

    private static final Logger log = Logger.getLogger(TritonModelRepositoryClient.class.getName());

    private final GRPCInferenceServiceGrpc.GRPCInferenceServiceBlockingV2Stub grpcStub;

    public TritonModelRepositoryClient(TritonConfig config) {
        this.grpcStub = TritonGrpc.newStub(config);
    }

    public boolean isModelReady(String modelName) {
        var request =
                GrpcService.ModelReadyRequest.newBuilder().setName(modelName).build();
        var response = TritonGrpc.invoke(
                statusStub(), invocation -> invocation.modelReady(request), "Failed to check model ready");
        return response.getReady();
    }

    private GRPCInferenceServiceGrpc.GRPCInferenceServiceBlockingV2Stub statusStub() {
        return grpcStub.withDeadlineAfter(STATUS_CHECK_TIMEOUT);
    }

    enum ModelActivity {
        INACTIVE,
        LOADED_OR_LOADING,
        UNLOADING
    }

    // Package-private for tests.
    ModelActivity modelActivity(String modelName) {
        boolean unloading = false;

        for (var model : getRepositoryIndex()) {
            if (!modelName.equals(model.getName())) {
                continue;
            }

            if ("READY".equals(model.getState()) || "LOADING".equals(model.getState())) {
                return ModelActivity.LOADED_OR_LOADING;
            }

            if ("UNLOADING".equals(model.getState())) {
                unloading = true;
            }
        }

        return unloading ? ModelActivity.UNLOADING : ModelActivity.INACTIVE;
    }

    private List<GrpcService.RepositoryIndexResponse.ModelIndex> getRepositoryIndex() {
        var request = GrpcService.RepositoryIndexRequest.newBuilder().build();
        var response = TritonGrpc.invoke(statusStub(), s -> s.repositoryIndex(request), "Failed to get repository index");
        return response.getModelsList();
    }

    public void loadModel(String modelName) {
        log.fine(() -> "Loading model " + modelName);
        var request = GrpcService.RepositoryModelLoadRequest.newBuilder()
                .setModelName(modelName)
                .build();
        TritonGrpc.invoke(grpcStub.withDeadlineAfter(LOAD_TIMEOUT),
                s -> s.repositoryModelLoad(request), "Failed to load model");
    }

    public void unloadModel(String modelName) {
        log.fine(() -> "Unloading model " + modelName);
        var request = GrpcService.RepositoryModelUnloadRequest.newBuilder()
                .setModelName(modelName)
                .build();
        TritonGrpc.invoke(grpcStub.withDeadlineAfter(UNLOAD_TIMEOUT),
                s -> s.repositoryModelUnload(request), "Failed to unload model");
    }

    public void unloadAllModels() {
        unloadModels(modelName -> true);
    }

    /** Unloads the loaded models whose names match. */
    public void unloadModels(Predicate<String> modelNameFilter) {
        log.fine(() -> "Unloading models");

        getRepositoryIndex().stream()
                .filter(model -> "READY".equals(model.getState())
                        || "LOADING".equals(model.getState())
                        || "UNLOADING".equals(model.getState()))
                .map(GrpcService.RepositoryIndexResponse.ModelIndex::getName)
                .distinct()
                .filter(modelNameFilter)
                .forEach(this::unloadUntilModelNotReady);
    }

    /**
     * Makes several attempts to load the model and checks its readiness.
     * This mitigates the timing issue because of delay between model files are copied to model repository
     * and the model can be loaded.
     */
    public void loadUntilModelReady(String modelName) {
        try {
            if (isModelReady(modelName)) {
                return;
            }
        } catch (TritonException | TimeoutException e) {
            // A failure must not abort before the first attempt to load the model.
        }

        boolean isLoaded = false;
        RuntimeException lastException = null;
        long startMillis = System.currentTimeMillis();

        for (int attempt = 0; attempt < MAX_MODEL_LOAD_ATTEMPTS; attempt++) {
            try {
                if (!isLoaded) { // We only need one successful load request.
                    loadModel(modelName);
                    isLoaded = true;
                }

                if (isModelReady(modelName)) {
                    return;
                }
            } catch (TritonException | TimeoutException e) {
                // Triton rejects a config the same way on every attempt.
                if (e instanceof TritonException tritonException && isConfigRejection(tritonException)) {
                    throw tritonException;
                }
                lastException = e;
            }

            if (attempt < MAX_MODEL_LOAD_ATTEMPTS - 1) {
                try {
                    Thread.sleep(MAX_MODEL_LOAD_WAIT_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new TritonException("Interrupted loading model " + modelName, e);
                }
            }
        }

        var waitMillis = System.currentTimeMillis() - startMillis;
        var exceptionMessage = "Failed loading model " + modelName + " after " + waitMillis + " ms";
        throw new TritonException(exceptionMessage, lastException);
    }

    /**
     * Returns whether the error is Triton refusing to load a model with its config, such as rejecting batching
     * or inputs and outputs specified in the model config that do not match the model.
     * Triton reports a model file that is missing or partly written with other errors, which a retry may fix.
     */
    public static boolean isConfigRejection(TritonException e) {
        return Exceptions.toMessageString(e).contains("UNAVAILABLE state: Invalid argument:");
    }

    /**
     * Makes several attempts to unload the model and waits until it is completely inactive.
     */
    public void unloadUntilModelNotReady(String modelName) {
        boolean unloadRequested = false;
        RuntimeException lastException = null;
        long startMillis = System.currentTimeMillis();

        for (int attempt = 0; attempt < MAX_MODEL_UNLOAD_ATTEMPTS; attempt++) {
            try {
                var activity = modelActivity(modelName);

                if (activity == ModelActivity.INACTIVE) {
                    return;
                }

                // If Triton is already unloading the model, only poll.
                if (!unloadRequested && activity != ModelActivity.UNLOADING) {
                    unloadModel(modelName);
                    unloadRequested = true;

                    if (modelActivity(modelName) == ModelActivity.INACTIVE) {
                        return;
                    }
                }
            } catch (TritonException | TimeoutException e) {
                lastException = e;
            }

            if (attempt < MAX_MODEL_UNLOAD_ATTEMPTS - 1) {
                try {
                    Thread.sleep(MAX_MODEL_UNLOAD_WAIT_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new TritonException("Interrupted unloading model " + modelName, e);
                }
            }
        }

        var waitMillis = System.currentTimeMillis() - startMillis;
        var exceptionMessage = "Failed unloading model " + modelName + " after " + waitMillis + " ms";
        throw new TritonException(exceptionMessage, lastException);
    }

    @Override
    public void close() {
        TritonGrpc.close(grpcStub);
    }

}
