// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import ai.vespa.llm.clients.TritonConfig;
import ai.vespa.modelintegration.evaluator.OnnxEvaluatorOptions;
import ai.vespa.modelintegration.llamacpp.LlamacppOptions;
import com.yahoo.language.process.InvalidInputException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests TritonLlamacppRuntime against Vespa's Triton image with llama.cpp (Triton 26.11 or later), e.g.
 * -DVESPA_USE_TRITON=true -DVESPA_TRITON_IMAGE=localhost/tritonserver:<tag>-cpu-onnx-llamacpp
 *
 * @author ingridhabrekke
 */
@EnabledIfSystemProperty(named = "VESPA_USE_TRITON", matches = "true")
@ExtendWith(ContainerEnvironmentAvailableCondition.class)
class TritonLlamacppRuntimeTest {

    private static final String GGUF_MODEL_PATH = "src/test/models/llm/tinyllm.gguf";
    private static final String ONNX_MODEL_PATH = "src/test/models/onnx/transformer/dummy_transformer.onnx";

    // Each test uses its own seed, and thereby model name: under Podman on macOS, Triton may not see a model
    // directory that is deleted and created again with the same name.
    // The test model does not pool by default, which llama-server's OpenAI embeddings endpoint requires.
    private static LlamacppOptions options(int seed) {
        return new LlamacppOptions(1, 0, -1, -1, 0, Optional.of(LlamacppOptions.Pooling.MEAN), seed, 0, 0, false);
    }

    private static TritonServerContainer tritonContainer;

    @BeforeAll
    static void setupTritonContainer() throws IOException {
        assumeTrue(TritonServerContainer.isLlamacppImageConfigured(), "Needs a Vespa Triton image with llama.cpp");
        tritonContainer = TritonServerContainer.withLlamacpp();
        tritonContainer.start();
    }

    @AfterAll
    static void stopTritonContainer() {
        if (tritonContainer != null) tritonContainer.stop();
    }

    @Test
    void embeds_and_tokenizes_with_the_model_in_triton() {
        var runtime = new TritonLlamacppRuntime(config());
        try (var evaluator = runtime.evaluatorOf(GGUF_MODEL_PATH, options(1))) {
            var embedding = evaluator.embed("This is a test", Optional.empty());
            assertEquals(64, embedding.length);
            assertTrue(norm(embedding) > 1.5, "Not normalized: " + norm(embedding));

            // Detokenized text may differ in leading whitespace, depending on the tokenizer.
            var tokens = evaluator.tokenize("This is a test", Optional.empty());
            assertTrue(tokens.length > 0);
            assertEquals("This is a test", evaluator.detokenize(tokens, Optional.empty()).strip());
        } finally {
            runtime.deconstruct();
        }
    }

    @Test
    void input_longer_than_the_context_is_invalid_input() {
        var runtime = new TritonLlamacppRuntime(config());
        try (var evaluator = runtime.evaluatorOf(GGUF_MODEL_PATH, options(2))) {
            assertThrows(InvalidInputException.class, () -> evaluator.embed("This is a test. ".repeat(1000), Optional.empty()));
        } finally {
            runtime.deconstruct();
        }
    }

    @Test
    void model_is_unloaded_and_its_files_deleted_when_the_last_evaluator_closes() {
        var runtime = new TritonLlamacppRuntime(config());
        var client = new TritonModelRepositoryClient(config());
        try {
            var modelName = TritonLlamacppRuntime.generateModelName(GGUF_MODEL_PATH, options(3));
            var modelDir = tritonContainer.getModelRepositoryPath().resolve(modelName);
            var first = runtime.evaluatorOf(GGUF_MODEL_PATH, options(3));
            var second = runtime.evaluatorOf(GGUF_MODEL_PATH, options(3));
            assertTrue(client.isModelReady(modelName));
            assertTrue(Files.isRegularFile(modelDir.resolve("1/model.gguf")));

            first.close();
            assertTrue(client.isModelReady(modelName));

            second.close();
            assertFalse(client.isModelReady(modelName));
            assertFalse(Files.exists(modelDir));
        } finally {
            runtime.deconstruct();
            client.close();
        }
    }

    @Test
    void each_runtime_cleans_up_only_its_own_leftovers() throws IOException {
        var client = new TritonModelRepositoryClient(config());
        var llamacppModel = TritonLlamacppRuntime.generateModelName(GGUF_MODEL_PATH, options(4));
        // Runtimes of a previous process, which leave their models loaded.
        var previousLlamacppRuntime = new TritonLlamacppRuntime(config());
        var previousOnnxRuntime = new TritonOnnxRuntime(config());
        previousLlamacppRuntime.evaluatorOf(GGUF_MODEL_PATH, options(4));
        previousOnnxRuntime.evaluatorOf(ONNX_MODEL_PATH, new OnnxEvaluatorOptions.Builder(8).build());
        try {
            assertTrue(client.isModelReady(llamacppModel));
            assertEquals(List.of(llamacppModel), loadedModelsOf(client, true));
            assertEquals(1, loadedModelsOf(client, false).size());

            // An ONNX runtime starting up removes the leftover ONNX model, and keeps the llama.cpp model.
            new TritonOnnxRuntime(config()).deconstruct();
            assertEquals(List.of(llamacppModel), loadedModelsOf(client, true));
            assertEquals(List.of(), loadedModelsOf(client, false));

            // A llama.cpp runtime starting up removes the leftover llama.cpp model, and keeps an ONNX model.
            previousOnnxRuntime.evaluatorOf(ONNX_MODEL_PATH, new OnnxEvaluatorOptions.Builder(8).setExecutionMode(OnnxEvaluatorOptions.ExecutionMode.PARALLEL).build());
            new TritonLlamacppRuntime(config()).deconstruct();
            assertFalse(client.isModelReady(llamacppModel));
            assertEquals(List.of(), loadedModelsOf(client, true));
            assertEquals(1, loadedModelsOf(client, false).size());
        } finally {
            new TritonOnnxRuntime(config()).deconstruct(); // Removes the ONNX model
            client.close();
        }
    }

    // The models in the repository that Triton has ready, of llama.cpp or other backends.
    private static List<String> loadedModelsOf(TritonModelRepositoryClient client, boolean llamacpp) throws IOException {
        try (var dirs = Files.list(tritonContainer.getModelRepositoryPath())) {
            return dirs.filter(dir -> TritonLlamacppRuntime.isLlamacppModel(dir) == llamacpp)
                       .map(dir -> dir.getFileName().toString())
                       .filter(client::isModelReady)
                       .toList();
        }
    }

    private static double norm(float[] vector) {
        double sum = 0;
        for (float value : vector) sum += value * value;
        return Math.sqrt(sum);
    }

    private static TritonConfig config() {
        return new TritonConfig.Builder()
                .target(tritonContainer.getGrpcEndpoint())
                .llamacppEndpoint(tritonContainer.getLlamacppEndpoint())
                .modelControlMode(TritonConfig.ModelControlMode.EXPLICIT)
                .modelRepositoryPath(tritonContainer.getModelRepositoryPath().toString())
                .build();
    }

}
