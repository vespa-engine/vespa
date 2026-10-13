// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.evaluator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bjorncs
 */
class OnnxStreamParserTest {

    @Test
    void extracts_external_data_locations_from_model_file() throws IOException {
        assertEquals(
                Set.of(Paths.get("external_data.bin")),
                OnnxStreamParser.getExternalDataLocations(
                        Path.of("src/test/models/onnx/external_data/add_with_external_data.onnx")));
    }

    @Test
    void extracts_operator_types_from_model_file() throws IOException {
        var model = Path.of("src/test/models/onnx/external_data/add_with_external_data.onnx");
        assertEquals(Set.of("Add"), OnnxStreamParser.getOperatorTypes(model));
    }

    @Test
    void finds_dynamic_quantization_operators() throws IOException {
        var quantized = OnnxStreamParser.getOperatorTypes(Path.of("src/test/models/onnx/transformer/embedding_model.onnx"));
        assertTrue(quantized.contains("DynamicQuantizeLinear"), quantized.toString());
        assertTrue(quantized.contains("MatMulInteger"), quantized.toString());

        var float32 = OnnxStreamParser.getOperatorTypes(Path.of("src/test/models/onnx/transformer/dummy_masked_attention_embedder.onnx"));
        assertFalse(float32.contains("DynamicQuantizeLinear"), float32.toString());
        assertTrue(float32.contains("Softmax"), float32.toString());
    }

}
