// Copyright Vespa.ai. Licensed under the Apache License, Version 2.0.
package ai.vespa.triton;

import ai.vespa.embedding.HuggingFaceEmbedder;
import ai.vespa.llm.clients.TritonConfig;
import ai.vespa.modelintegration.evaluator.EmbeddedOnnxRuntime;
import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import ai.vespa.modelintegration.evaluator.config.OnnxEvaluatorConfig;
import com.yahoo.config.ModelReference;
import com.yahoo.embedding.huggingface.HuggingFaceEmbedderConfig;
import com.yahoo.embedding.huggingface.HuggingFaceEmbedderConfig.PoolingStrategy;
import com.yahoo.language.process.Embedder;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that Triton pools embeddings the same as HuggingFaceEmbedder does with the embedded runtime.
 * Requires a Vespa Triton image that supports pooling in the ONNX Runtime backend:
 * -DVESPA_USE_TRITON=true -DVESPA_TRITON_IMAGE=localhost/tritonserver:<tag>
 *
 * @author glebashnik
 */
@EnabledIfSystemProperty(named = "VESPA_USE_TRITON", matches = "true")
@EnabledIfSystemProperty(named = "VESPA_TRITON_IMAGE", matches = ".+")
@ExtendWith(ContainerEnvironmentAvailableCondition.class)
class TritonPoolingEmbedderTest {

    private static final String TOKENIZER_PATH = "src/test/models/onnx/transformer/real_tokenizer.json";
    private static final String MODEL_PATH = "src/test/models/onnx/transformer/embedding_model.onnx";

    private static final List<String> TEXTS = List.of(
            "This is a test",
            "A somewhat longer text, so that the mean is taken over more tokens than the first text has.");

    // The full embedding, a truncated one, normalized after truncation, and a binarized one.
    private static final List<TensorType> TARGET_TYPES = List.of(
            TensorType.fromSpec("tensor<float>(x[384])"),
            TensorType.fromSpec("tensor<float>(x[8])"),
            TensorType.fromSpec("tensor<int8>(x[16])"));

    private static TritonServerContainer tritonContainer;
    private static TritonOnnxRuntime tritonRuntime;
    private static EmbeddedOnnxRuntime embeddedRuntime;

    @BeforeAll
    static void setup() throws IOException {
        tritonContainer = new TritonServerContainer();
        tritonContainer.start();
        tritonRuntime = new TritonOnnxRuntime(new TritonConfig.Builder()
                .target(tritonContainer.getGrpcEndpoint())
                .modelRepositoryPath(tritonContainer.getModelRepositoryPath().toString())
                .onnxPooling(true)
                .build());
        embeddedRuntime = EmbeddedOnnxRuntime.createTestInstance();
    }

    @AfterAll
    static void tearDown() {
        if (tritonRuntime != null) tritonRuntime.deconstruct();
        if (embeddedRuntime != null) embeddedRuntime.deconstruct();
        if (tritonContainer != null) tritonContainer.stop();
    }

    static Stream<Arguments> strategies() {
        return Stream.of(PoolingStrategy.Enum.mean, PoolingStrategy.Enum.cls, PoolingStrategy.Enum.last)
                .flatMap(strategy -> Stream.of(Arguments.of(strategy, false), Arguments.of(strategy, true)));
    }

    @ParameterizedTest
    @MethodSource("strategies")
    void triton_pooling_equals_embedder_pooling(PoolingStrategy.Enum strategy, boolean normalize) throws IOException {
        var config = new HuggingFaceEmbedderConfig.Builder()
                .tokenizerPath(ModelReference.valueOf(TOKENIZER_PATH))
                .transformerModel(ModelReference.valueOf(MODEL_PATH))
                .poolingStrategy(strategy)
                .normalize(normalize)
                .build();
        var tritonEmbedder = createEmbedder(tritonRuntime, config);
        var embeddedEmbedder = createEmbedder(embeddedRuntime, config);

        try {
            // Creating the embedder fails unless Triton returns 2-dimensional pooled embeddings.
            assertPoolingConfigured(strategy);

            for (var text : TEXTS) {
                for (var type : TARGET_TYPES) {
                    var expected = embeddedEmbedder.embed(text, new Embedder.Context("schema.indexing"), type);
                    var actual = tritonEmbedder.embed(text, new Embedder.Context("schema.indexing"), type);
                    assertEmbeddingsEqual(expected, actual, strategy + ", normalize " + normalize + ", " + type + ", '" + text + "'");
                }
            }
        } finally {
            tritonEmbedder.deconstruct();
            embeddedEmbedder.deconstruct();
        }
    }

    private static HuggingFaceEmbedder createEmbedder(OnnxRuntime runtime, HuggingFaceEmbedderConfig config) {
        return new HuggingFaceEmbedder(runtime, Embedder.Runtime.testInstance(), config,
                                       new OnnxEvaluatorConfig.Builder().build(), ModelReference::value);
    }

    private static void assertPoolingConfigured(PoolingStrategy.Enum strategy) throws IOException {
        try (var models = Files.list(tritonContainer.getModelRepositoryPath())) {
            var configs = models.map(model -> model.resolve("config.pbtxt")).toList();
            assertEquals(1, configs.size(), "Expected one model in the repository: " + configs);
            var config = Files.readString(configs.get(0));
            assertTrue(config.contains("string_value: \"" + strategy + "\""), config);
        }
    }

    private static void assertEmbeddingsEqual(Tensor expected, Tensor actual, String message) {
        assertEquals(expected.type(), actual.type(), message);
        if (expected.type().valueType() == TensorType.Value.INT8) {
            assertEquals(expected, actual, message);
            return;
        }
        var expectedCells = expected.cellIterator();
        while (expectedCells.hasNext()) {
            var cell = expectedCells.next();
            assertEquals(cell.getValue(), actual.get(cell.getKey()), 1e-5, message + " at " + cell.getKey());
        }
    }
}
