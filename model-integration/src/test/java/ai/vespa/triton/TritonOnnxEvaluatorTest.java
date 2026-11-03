// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.triton;

import ai.vespa.llm.clients.TritonConfig;
import ai.vespa.modelintegration.evaluator.OnnxPadderOptions;
import ai.vespa.modelintegration.evaluator.OnnxPadder;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.language.process.TimeoutException;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * @author glebashnik
 */
@EnabledIfSystemProperty(named = "VESPA_USE_TRITON", matches = "true")
@ExtendWith(ContainerEnvironmentAvailableCondition.class)
class TritonOnnxEvaluatorTest {
    private static final String MODEL_NAME = "dummy_transformer";
    /** A float model with BERT-like masked attention, whose output for the real tokens is unchanged by padding. */
    private static final String MASKED_MODEL_NAME = "dummy_masked_attention_embedder";

    private static TritonServerContainer tritonContainer;

    @BeforeAll
    static void launchTritonServer() throws IOException {
        tritonContainer = new TritonServerContainer();
        tritonContainer.addModel(MODEL_NAME, Path.of("src/test/models/onnx/transformer/" + MODEL_NAME + ".onnx"));
        tritonContainer.addModel(MASKED_MODEL_NAME, Path.of("src/test/models/onnx/transformer/" + MASKED_MODEL_NAME + ".onnx"));
        tritonContainer.start();
    }

    /** Padding pads inputs to the buckets and unpads the outputs, which the real tokens' output is unchanged by */
    @Test
    void outputs_are_the_same_with_padding_and_cut_back_to_the_real_length() {
        try (var tritonClient = createTritonClient()) {
            tritonClient.loadModel(MASKED_MODEL_NAME);
            var modelReference = mock(ResourceReference.class);
            var unpadded = new TritonOnnxEvaluator(MASKED_MODEL_NAME, modelReference, tritonClient, false, Optional.empty());
            var padded = OnnxPadder.of(OnnxPadderOptions.of(List.of(8, 16)), unpadded, MASKED_MODEL_NAME);
            assertTrue(padded instanceof OnnxPadder, "Verified with a sample at construction");

            var inputs = inputs(List.of(101L, 2023L, 2003L, 102L));
            var expected = unpadded.evaluate(inputs);
            var actual = padded.evaluate(inputs);
            assertEquals(TensorType.fromSpec("tensor<float>(d0[1],d1[4],d2[16])"), actual.get("last_hidden_state").type());
            assertClose(expected.get("last_hidden_state"), actual.get("last_hidden_state"));

            var longer = inputs(List.of(101L, 2023L, 2003L, 2001L, 2002L, 2004L, 2005L, 2006L, 2007L, 102L));
            assertClose(unpadded.evaluate(longer, "last_hidden_state"),
                                                         padded.evaluate(longer, "last_hidden_state"));
        }
    }

    @Test
    void evaluate_with_output_name() {
        try (var tritonClient = createTritonClient()) {
            tritonClient.loadModel(MODEL_NAME);
            var modelReference = mock(ResourceReference.class);
            var evaluator = new TritonOnnxEvaluator(MODEL_NAME, modelReference, tritonClient, false, Optional.empty());

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

            var output = evaluator.evaluate(inputs, "output_0");
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
    void evaluate_throws_timeout_exception_when_deadline_fires() {
        try (var tritonClient = createTritonClient()) {
            tritonClient.loadModel(MODEL_NAME);
            var modelReference = mock(ResourceReference.class);
            var evaluator = new TritonOnnxEvaluator(MODEL_NAME, modelReference, tritonClient, false, Optional.empty());

            var inputs = Map.of(
                    "input_ids",      Tensor.from("tensor<float>(d0[1],d1[5]):[1.0, 2.0, 3.0, 4.0, 5.0]"),
                    "attention_mask", Tensor.from("tensor<float>(d0[1],d1[5]):[1.0, 1.0, 1.0, 1.0, 1.0]"),
                    "token_type_ids", Tensor.from("tensor<float>(d0[1],d1[5]):[0.0, 0.0, 0.0, 0.0, 0.0]"));

            assertThrows(TimeoutException.class,
                    () -> evaluator.evaluate(inputs, "output_0", Duration.ofMillis(1)));
        }
    }

    private static Map<String, Tensor> inputs(List<Long> ids) {
        var type = TensorType.fromSpec("tensor<float>(d0[1],d1[" + ids.size() + "])");
        var idsBuilder = Tensor.Builder.of(type);
        var maskBuilder = Tensor.Builder.of(type);
        for (int i = 0; i < ids.size(); i++) {
            idsBuilder.cell(ids.get(i), 0, i);
            maskBuilder.cell(1, 0, i);
        }
        return Map.of("input_ids", idsBuilder.build(), "attention_mask", maskBuilder.build());
    }

    private static void assertClose(Tensor expected, Tensor actual) {
        assertEquals(expected.type(), actual.type());
        var expectedCells = (IndexedTensor) expected;
        var actualCells = (IndexedTensor) actual;
        for (long i = 0; i < expectedCells.size(); i++)
            assertEquals(expectedCells.get(i), actualCells.get(i), 1e-5, "Cell " + i);
    }

    private static TritonOnnxClient createTritonClient() {
        return new TritonOnnxClient(new TritonConfig.Builder()
                .target(tritonContainer.getGrpcEndpoint())
                .build());
    }
}
