// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import com.yahoo.language.process.InvalidInputException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests when TritonLlamacppEvaluator reloads a model in Triton.
 *
 * @author ingridhabrekke
 * @author glebashnik
 */
class TritonLlamacppEvaluatorTest {

    private static final String MODEL = "model_abc";
    private static final float[] EMBEDDING = { 1, 2 };

    private final TritonLlamacppClient llamacppClient = mock(TritonLlamacppClient.class);
    private final TritonModelRepositoryClient repositoryClient = mock(TritonModelRepositoryClient.class);
    private final AtomicInteger releases = new AtomicInteger();

    @Test
    void model_that_triton_no_longer_has_is_reloaded_and_the_request_retried() {
        when(llamacppClient.embed(eq(MODEL), eq("text"), any()))
                .thenThrow(new TritonException("Request to /v1/embeddings failed with status 422"))
                .thenReturn(EMBEDDING);
        when(repositoryClient.isModelReady(MODEL)).thenReturn(false);

        assertArrayEquals(EMBEDDING, evaluator(true).embed("text", Optional.empty()));
        verify(repositoryClient).loadUntilModelReady(MODEL);
    }

    @Test
    void retry_after_reload_keeps_the_request_deadline() {
        var deadline = Optional.of(Instant.now().plusSeconds(10));
        when(llamacppClient.embed(eq(MODEL), eq("text"), any()))
                .thenThrow(new TritonException("Request to /v1/embeddings failed with status 422"))
                .thenReturn(EMBEDDING);
        when(repositoryClient.isModelReady(MODEL)).thenReturn(false);

        evaluator(true).embed("text", deadline);
        // The same absolute deadline, so time spent reloading counts against it.
        verify(llamacppClient, times(2)).embed(MODEL, "text", deadline);
    }

    @Test
    void failure_of_a_model_that_triton_has_is_not_retried() {
        var failure = new TritonException("Request to /v1/embeddings failed with status 500");
        when(llamacppClient.embed(eq(MODEL), eq("text"), any())).thenThrow(failure);
        when(repositoryClient.isModelReady(MODEL)).thenReturn(true);

        assertEquals(failure, assertThrows(TritonException.class, () -> evaluator(true).embed("text", Optional.empty())));
        verify(repositoryClient, never()).loadUntilModelReady(any());
    }

    @Test
    void models_are_not_reloaded_without_explicit_model_control() {
        when(llamacppClient.embed(eq(MODEL), eq("text"), any())).thenThrow(new TritonException("failed"));

        assertThrows(TritonException.class, () -> evaluator(false).embed("text", Optional.empty()));
        verify(repositoryClient, never()).isModelReady(any());
        verify(repositoryClient, never()).loadUntilModelReady(any());
    }

    @Test
    void invalid_input_is_not_retried() {
        when(llamacppClient.tokenize(eq(MODEL), eq("text"), any())).thenThrow(new InvalidInputException("too long"));

        assertThrows(InvalidInputException.class, () -> evaluator(true).tokenize("text", Optional.empty()));
        verify(repositoryClient, never()).loadUntilModelReady(any());
    }

    @Test
    void closing_releases_the_model() {
        evaluator(true).close();
        assertEquals(1, releases.get());
    }

    private TritonLlamacppEvaluator evaluator(boolean isModelControlExplicit) {
        return new TritonLlamacppEvaluator(MODEL, releases::incrementAndGet, llamacppClient, repositoryClient,
                                           isModelControlExplicit);
    }

}
