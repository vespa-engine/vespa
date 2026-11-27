// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import ai.vespa.llm.clients.TritonConfig;
import ai.vespa.modelintegration.llamacpp.LlamacppOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the model reference counting of TritonLlamacppRuntime, with mocked Triton clients.
 *
 * @author ingridhabrekke
 * @author glebashnik
 */
class TritonLlamacppRuntimeReferenceTest {

    private static final LlamacppOptions OPTIONS =
            new LlamacppOptions(1, 0, -1, -1, 0, Optional.empty(), -1, 0, 0, false);

    private final TritonModelRepositoryClient repositoryClient = mock(TritonModelRepositoryClient.class);
    private final TritonLlamacppClient llamacppClient = mock(TritonLlamacppClient.class);

    @Test
    void a_model_in_use_is_not_reloaded_or_cleaned_up_when_another_evaluator_references_it(@TempDir Path directory) throws IOException {
        var repository = Files.createDirectories(directory.resolve("models"));
        var modelPath = Files.writeString(directory.resolve("model.gguf"), "gguf").toString();
        var runtime = new TritonLlamacppRuntime(new TritonConfig.Builder().modelRepositoryPath(repository.toString()).build(),
                                                llamacppClient, repositoryClient);
        try {
            when(repositoryClient.isModelReady(anyString()))
                    .thenReturn(false) // First reference: not loaded yet
                    .thenThrow(new TritonException("Triton is briefly unavailable"));

            var first = runtime.evaluatorOf(modelPath, OPTIONS);
            var modelName = TritonLlamacppRuntime.generateModelName(modelPath, OPTIONS);
            verify(repositoryClient, times(1)).loadUntilModelReady(modelName);

            // A second reference to the model in use, e.g. by the new embedder of a reconfiguration
            var second = runtime.evaluatorOf(modelPath, OPTIONS);
            verify(repositoryClient, times(1)).loadUntilModelReady(modelName);
            verify(repositoryClient, never()).unloadUntilModelNotReady(anyString());
            assertTrue(Files.exists(repository.resolve(modelName).resolve("1").resolve("model.gguf")));

            // The model is cleaned up only when its last reference is released
            first.close();
            verify(repositoryClient, never()).unloadUntilModelNotReady(anyString());
            second.close();
            verify(repositoryClient, times(1)).unloadUntilModelNotReady(modelName);
        } finally {
            runtime.deconstruct();
        }
    }

    @Test
    void a_model_that_fails_to_load_gets_no_reference(@TempDir Path directory) throws IOException {
        var repository = Files.createDirectories(directory.resolve("models"));
        var modelPath = Files.writeString(directory.resolve("model.gguf"), "gguf").toString();
        var runtime = new TritonLlamacppRuntime(new TritonConfig.Builder().modelRepositoryPath(repository.toString()).build(),
                                                llamacppClient, repositoryClient);
        try {
            var modelName = TritonLlamacppRuntime.generateModelName(modelPath, OPTIONS);
            when(repositoryClient.isModelReady(anyString())).thenReturn(false);
            doThrow(new TritonException("Failed to load")).doNothing().when(repositoryClient).loadUntilModelReady(modelName);

            assertThrows(TritonException.class, () -> runtime.evaluatorOf(modelPath, OPTIONS));
            verify(repositoryClient, times(1)).unloadUntilModelNotReady(modelName);
            assertFalse(Files.exists(repository.resolve(modelName)));

            // The next evaluator loads the model, since the failed one holds no reference to it
            runtime.evaluatorOf(modelPath, OPTIONS).close();
            verify(repositoryClient, times(2)).loadUntilModelReady(modelName);
            verify(repositoryClient, times(2)).unloadUntilModelNotReady(modelName);
        } finally {
            runtime.deconstruct();
        }
    }

    // Hammers concurrent create and close of the same model from multiple threads,
    // exercising the races between evaluatorOf and evaluator close.
    // A race shows up as a load of a loaded model or an unload of an unloaded one,
    // and a deadlock fails the test through the future timeouts.
    @Test
    void concurrent_create_and_close_of_the_same_model(@TempDir Path directory) throws Exception {
        var repository = Files.createDirectories(directory.resolve("models"));
        var modelPath = Files.writeString(directory.resolve("model.gguf"), "gguf").toString();
        var runtime = new TritonLlamacppRuntime(new TritonConfig.Builder().modelRepositoryPath(repository.toString()).build(),
                                                llamacppClient, repositoryClient);
        var isLoaded = new AtomicBoolean(false);
        var loads = new AtomicInteger();
        when(repositoryClient.isModelReady(anyString())).thenAnswer(invocation -> isLoaded.get());
        doAnswer(invocation -> {
            assertTrue(isLoaded.compareAndSet(false, true), "Loaded a loaded model");
            loads.incrementAndGet();
            return null;
        }).when(repositoryClient).loadUntilModelReady(anyString());
        doAnswer(invocation -> {
            assertTrue(isLoaded.compareAndSet(true, false), "Unloaded an unloaded model");
            return null;
        }).when(repositoryClient).unloadUntilModelNotReady(anyString());

        var threads = 4;
        var iterations = 250;
        var executor = Executors.newFixedThreadPool(threads);
        try {
            var futures = new ArrayList<Future<?>>();
            for (int thread = 0; thread < threads; thread++) {
                futures.add(executor.submit(() -> {
                    for (int i = 0; i < iterations; i++)
                        runtime.evaluatorOf(modelPath, OPTIONS).close();
                    return null;
                }));
            }
            for (var future : futures)
                future.get(1, TimeUnit.MINUTES);
        } finally {
            executor.shutdownNow();
            runtime.deconstruct();
        }
        assertFalse(isLoaded.get());
        assertTrue(loads.get() > 0);
        assertFalse(Files.exists(repository.resolve(TritonLlamacppRuntime.generateModelName(modelPath, OPTIONS))));
    }

}
