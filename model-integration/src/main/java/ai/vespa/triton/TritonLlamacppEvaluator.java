// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import ai.vespa.modelintegration.llamacpp.LlamacppEvaluator;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.language.process.TimeoutException;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A GGUF embedding model that llama.cpp serves in Triton.
 *
 * @author ingridhabrekke
 */
class TritonLlamacppEvaluator implements LlamacppEvaluator {

    private static final Logger log = Logger.getLogger(TritonLlamacppEvaluator.class.getName());

    private final String modelName;
    private final ResourceReference modelReference;
    private final TritonLlamacppClient llamacppClient;
    private final TritonModelRepositoryClient repositoryClient;
    private final boolean isExplicitControl;

    TritonLlamacppEvaluator(String modelName, ResourceReference modelReference, TritonLlamacppClient llamacppClient,
                         TritonModelRepositoryClient repositoryClient, boolean isExplicitControl) {
        this.modelName = modelName;
        this.modelReference = modelReference;
        this.llamacppClient = llamacppClient;
        this.repositoryClient = repositoryClient;
        this.isExplicitControl = isExplicitControl;
    }

    @Override
    public float[] embed(String text, Optional<Instant> deadline) {
        return withReload(() -> llamacppClient.embed(modelName, text, deadline));
    }

    @Override
    public int[] tokenize(String text, Optional<Instant> deadline) {
        return withReload(() -> llamacppClient.tokenize(modelName, text, deadline));
    }

    @Override
    public String detokenize(int[] tokens, Optional<Instant> deadline) {
        return withReload(() -> llamacppClient.detokenize(modelName, tokens, deadline));
    }

    /**
     * Retries once after loading the model again if Triton no longer has it, e.g. after Triton restarted.
     * Triton's OpenAI frontend does not report a model it does not have with a distinct status,
     * so this asks Triton after any failure other than invalid input and timeouts.
     * A model that Triton has, but whose llama-server is restarting, is not reloaded:
     * the backend restarts llama-server itself.
     * As in TritonOnnxEvaluator, the retry keeps the request's deadline, so a retry after a slow reload times out.
     */
    private <T> T withReload(Supplier<T> request) {
        try {
            return request.get();
        } catch (TritonException e) {
            if ( ! isExplicitControl || isModelReady()) throw e;

            log.log(Level.WARNING, "Failed to run llama.cpp model " + modelName + " in Triton, will retry after reload.", e);
            repositoryClient.loadUntilModelReady(modelName);
            return request.get();
        }
    }

    private boolean isModelReady() {
        try {
            return repositoryClient.isModelReady(modelName);
        } catch (TritonException | TimeoutException e) {
            return false; // Loading reports why Triton is unavailable
        }
    }

    @Override
    public void close() {
        modelReference.close();
    }

}
