// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.evaluator;

import onnx.Onnx;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bjorncs
 * @author ingridhabrekke
 * @author glebashnik
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
    void extracts_external_data_locations_from_tensor_attributes(@TempDir Path directory) throws IOException {
        var model = Files.write(directory.resolve("model.onnx"), Onnx.ModelProto.newBuilder()
                .setGraph(Onnx.GraphProto.newBuilder().addNode(constantNode("constant.bin"))).build().toByteArray());
        assertEquals(Set.of(Paths.get("constant.bin")), OnnxStreamParser.getExternalDataLocations(model));
    }

    @Test
    void extracts_external_data_locations_from_functions(@TempDir Path directory) throws IOException {
        var function = Onnx.FunctionProto.newBuilder().setName("f").addNode(constantNode("function.bin"));
        var model = Files.write(directory.resolve("model.onnx"), Onnx.ModelProto.newBuilder()
                .setGraph(Onnx.GraphProto.newBuilder()).addFunctions(function).build().toByteArray());
        assertEquals(Set.of(Paths.get("function.bin")), OnnxStreamParser.getExternalDataLocations(model));
    }

    @Test
    void extracts_external_data_locations_from_training_graphs(@TempDir Path directory) throws IOException {
        var trainingInfo = Onnx.TrainingInfoProto.newBuilder()
                .setAlgorithm(Onnx.GraphProto.newBuilder().addNode(constantNode("training.bin")));
        var model = Files.write(directory.resolve("model.onnx"), Onnx.ModelProto.newBuilder()
                .setGraph(Onnx.GraphProto.newBuilder()).addTrainingInfo(trainingInfo).build().toByteArray());
        assertEquals(Set.of(Paths.get("training.bin")), OnnxStreamParser.getExternalDataLocations(model));
    }

    @Test
    void extracts_external_data_locations_from_subgraphs(@TempDir Path directory) throws IOException {
        var branch = Onnx.NodeProto.newBuilder()
                .setOpType("If")
                .addAttribute(Onnx.AttributeProto.newBuilder()
                                      .setName("then_branch")
                                      .setG(Onnx.GraphProto.newBuilder().addInitializer(externalTensor("then.bin")))
                                      .setType(Onnx.AttributeProto.AttributeType.GRAPH));
        var model = Files.write(directory.resolve("model.onnx"), Onnx.ModelProto.newBuilder()
                .setGraph(Onnx.GraphProto.newBuilder().addNode(branch)).build().toByteArray());
        assertEquals(Set.of(Paths.get("then.bin")), OnnxStreamParser.getExternalDataLocations(model));
    }

    @Test
    void try_get_returns_no_external_data_locations_for_files_that_are_not_onnx(@TempDir Path directory)
            throws IOException {
        var notOnnx = Files.writeString(directory.resolve("not_onnx.onnx"), "not an onnx model");
        assertEquals(Set.of(), OnnxStreamParser.tryGetExternalDataLocations(notOnnx));

        var invalidPath = Files.write(directory.resolve("invalid_path.onnx"), modelWithExternalData("weights\0.bin"));
        assertEquals(Set.of(), OnnxStreamParser.tryGetExternalDataLocations(invalidPath));
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

    private static byte[] modelWithExternalData(String location) {
        return Onnx.ModelProto.newBuilder()
                .setGraph(Onnx.GraphProto.newBuilder().addInitializer(externalTensor(location)))
                .build().toByteArray();
    }

    private static Onnx.NodeProto constantNode(String location) {
        return Onnx.NodeProto.newBuilder()
                .setOpType("Constant")
                .addAttribute(Onnx.AttributeProto.newBuilder()
                                      .setName("value")
                                      .setT(externalTensor(location))
                                      .setType(Onnx.AttributeProto.AttributeType.TENSOR))
                .build();
    }

    private static Onnx.TensorProto externalTensor(String location) {
        return Onnx.TensorProto.newBuilder()
                .setName("weights")
                .setDataLocation(Onnx.TensorProto.DataLocation.EXTERNAL)
                .addExternalData(Onnx.StringStringEntryProto.newBuilder().setKey("location").setValue(location))
                .build();
    }

}
