// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import ai.vespa.llm.clients.TritonConfig;
import ai.vespa.modelintegration.evaluator.OnnxEvaluatorOptions;
import ai.vespa.modelintegration.llamacpp.LlamacppOptions;
import com.yahoo.tensor.Tensor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests ONNX and llama.cpp models in one Triton server, started with the default command of a Triton image
 * with a llama.cpp backend, given with
 * -DVESPA_USE_TRITON=true -DVESPA_TRITON_IMAGE=<image with llamacpp in its name>
 *
 * @author glebashnik
 */
@EnabledIfSystemProperty(named = "VESPA_USE_TRITON", matches = "true")
@ExtendWith(ContainerEnvironmentAvailableCondition.class)
class TritonOnnxAndLlamacppTest {

    private static final String ONNX_MODEL_PATH = "src/test/models/onnx/transformer/dummy_transformer.onnx";
    private static final String GGUF_MODEL_PATH = "src/test/models/llm/tinyllm.gguf";
    private static final Map<String, Tensor> ONNX_INPUTS = Map.of(
            "input_ids", Tensor.from("tensor<float>(d0[1],d1[5]):[1.0, 2.0, 3.0, 4.0, 5.0]"),
            "attention_mask", Tensor.from("tensor<float>(d0[1],d1[5]):[1.0, 1.0, 1.0, 1.0, 1.0]"),
            "token_type_ids", Tensor.from("tensor<float>(d0[1],d1[5]):[0.0, 0.0, 0.0, 0.0, 0.0]"));
    private static final String TEXT = "This is a test";

    // Two slots, each holding a whole micro-batch, so that llama-server batches the requests continuously.
    private static final LlamacppOptions LLAMACPP_OPTIONS =
            new LlamacppOptions(2, 1024, -1, 256, 0, Optional.of(LlamacppOptions.Pooling.MEAN), -1, 0, 0, false);

    private static TritonServerContainer tritonContainer;

    @BeforeAll
    static void setupTritonContainer() throws IOException {
        assumeTrue(TritonServerContainer.isLlamacppImageConfigured(), "Needs a Triton image with llama.cpp");
        tritonContainer = TritonServerContainer.withLlamacpp();
        tritonContainer.start();
    }

    @AfterAll
    static void stopTritonContainer() {
        if (tritonContainer != null) tritonContainer.stop();
    }

    // Triton batches the ONNX requests dynamically, while llama-server batches the llama.cpp requests.
    @Test
    void serves_onnx_and_llamacpp_models_at_the_same_time() throws Exception {
        var onnxRuntime = new TritonOnnxRuntime(config());
        var llamacppRuntime = new TritonLlamacppRuntime(config());
        var executor = Executors.newFixedThreadPool(16);
        try (var onnx = onnxRuntime.evaluatorOf(ONNX_MODEL_PATH, new OnnxEvaluatorOptions.Builder(8).setBatchingMaxSize(8).build());
             var llamacpp = llamacppRuntime.evaluatorOf(GGUF_MODEL_PATH, LLAMACPP_OPTIONS)) {
            var expectedOutput = onnx.evaluate(ONNX_INPUTS, "output_0", Optional.empty());
            var expectedEmbedding = llamacpp.embed(TEXT, Optional.empty());

            var onnxOutputs = new ArrayList<Future<Tensor>>();
            var embeddings = new ArrayList<Future<float[]>>();
            for (int i = 0; i < 64; i++) {
                onnxOutputs.add(executor.submit(() -> onnx.evaluate(ONNX_INPUTS, "output_0", Optional.empty())));
                embeddings.add(executor.submit(() -> llamacpp.embed(TEXT, Optional.empty())));
            }
            for (var output : onnxOutputs)
                assertEquals(expectedOutput.sum().asDouble(), output.get(1, TimeUnit.MINUTES).sum().asDouble(), 1e-5);
            for (var embedding : embeddings) {
                var similarity = cosine(expectedEmbedding, embedding.get(1, TimeUnit.MINUTES));
                assertTrue(similarity > 0.9999, "Cosine similarity " + similarity);
            }
        } finally {
            executor.shutdownNow();
            onnxRuntime.deconstruct();
            llamacppRuntime.deconstruct();
        }
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / Math.sqrt(normA * normB);
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
