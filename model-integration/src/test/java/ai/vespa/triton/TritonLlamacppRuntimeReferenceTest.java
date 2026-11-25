// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import ai.vespa.llm.clients.TritonConfig;
import ai.vespa.modelintegration.llamacpp.LlamacppOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the model reference counting of TritonLlamacppRuntime, with mocked Triton clients.
 *
 * @author ingridhabrekke
 */
class TritonLlamacppRuntimeReferenceTest {

    private static final LlamacppOptions OPTIONS =
            new LlamacppOptions(1, 0, -1, -1, 0, Optional.empty(), -1, 0, 0, false);

    private final TritonOnnxClient tritonClient = mock(TritonOnnxClient.class);
    private final TritonLlamacppClient llamacppClient = mock(TritonLlamacppClient.class);

    @Test
    void a_model_in_use_is_not_reloaded_or_cleaned_up_when_another_evaluator_references_it(@TempDir Path directory) throws IOException {
        var repository = Files.createDirectories(directory.resolve("models"));
        var modelPath = Files.writeString(directory.resolve("model.gguf"), "gguf").toString();
        var runtime = new TritonLlamacppRuntime(new TritonConfig.Builder().modelRepositoryPath(repository.toString()).build(),
                                                tritonClient, llamacppClient);
        try {
            when(tritonClient.isModelReady(anyString()))
                    .thenReturn(false) // First reference: not loaded yet
                    .thenThrow(new TritonOnnxClient.TritonException("Triton is briefly unavailable"));

            var first = runtime.evaluatorOf(modelPath, OPTIONS);
            var modelName = TritonLlamacppRuntime.generateModelName(modelPath, OPTIONS);
            verify(tritonClient, times(1)).loadUntilModelReady(modelName);

            // A second reference to the model in use, e.g. by the new embedder of a reconfiguration
            var second = runtime.evaluatorOf(modelPath, OPTIONS);
            verify(tritonClient, times(1)).loadUntilModelReady(modelName);
            verify(tritonClient, never()).unloadUntilModelNotReady(anyString());
            assertTrue(Files.exists(repository.resolve(modelName).resolve("1").resolve("model.gguf")));

            // The model is cleaned up only when its last reference is released
            first.close();
            verify(tritonClient, never()).unloadUntilModelNotReady(anyString());
            second.close();
            verify(tritonClient, times(1)).unloadUntilModelNotReady(modelName);
        } finally {
            runtime.deconstruct();
        }
    }

}
