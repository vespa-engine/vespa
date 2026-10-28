// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.evaluator;

import onnx.Onnx;

import java.util.List;

/**
 * Small models with and without a batch dimension.
 * OnnxStreamParserTest checks them against ONNX Runtime.
 * TritonOnnxRuntimeTest checks them against Triton.
 *
 * @author glebashnik
 */
public class BatchDimensionTestModels {

    /**
     * A model, and whether Triton supports batching for it.
     * Triton supports batching when ONNX Runtime reports a batch dimension for all inputs and outputs of the model.
     */
    public record Case(String name, Onnx.ModelProto model, boolean supportsBatching) {}

    /** Models whose batch dimension {@link OnnxStreamParser#declaresBatchDimension} reads as ONNX Runtime reports it */
    public static List<Case> cases() {
        return List.of(
                new Case("named", identity(tensor("x", "batch", 3), tensor("y", "batch", 3)), true),
                new Case("unnamed", identity(tensor("x", null, 3), tensor("y", null, 3)), true),
                // Some converters write a variable size as -1
                new Case("minus_one", identity(tensor("x", -1, 3), tensor("y", -1, 3)), true),
                // ONNX Runtime infers the shape of an output without one
                new Case("unshaped_output", identity(tensor("x", "batch", 3), unshaped("y")), true),
                // Before ONNX intermediate representation (IR) version 4, models list their initializers as inputs too, with fixed shapes
                new Case("initializer_input_ir3", matMulWithInitializerAsInput(3, 8), true),
                // From IR version 4, such an initializer is a default that a request may override
                new Case("initializer_input_ir8", matMulWithInitializerAsInput(8, 13), true),
                new Case("cross_encoder", crossEncoder(true), true),

                new Case("fixed_one", identity(tensor("x", 1, 3), tensor("y", 1, 3)), false),
                new Case("fixed_eight", identity(tensor("x", 8, 3), tensor("y", 8, 3)), false),
                new Case("fixed_output", identity(tensor("x", "batch", 3), tensor("y", 1, 3)), false),
                new Case("scalar", identity(tensor("x"), tensor("y")), false),
                // Some cross-encoders are exported to score one pair of a fixed token length at a time
                new Case("cross_encoder_fixed", crossEncoder(false), false),
                // ONNX Runtime reports an input without a shape as having no dimensions
                new Case("unshaped_input", identity(unshaped("x"), unshaped("y")), false));
    }

    /**
     * Models whose output ONNX Runtime infers to have no batch dimension, although the declared output has one.
     * {@link OnnxStreamParser#declaresBatchDimension} reads only declared shapes, so it reports a batch dimension for these.
     * Triton then fails to load them with a non-zero max batch size, and TritonOnnxRuntime loads them again with 0.
     */
    public static List<Case> casesWhereShapeInferenceRemovesBatchDimension() {
        var reduce = Onnx.GraphProto.newBuilder()
                .addNode(Onnx.NodeProto.newBuilder().setOpType("ReduceSum").addInput("x").addInput("axes").addOutput("y"))
                .addInitializer(Onnx.TensorProto.newBuilder().setName("axes").setDataType(7).addDims(1).addInt64Data(0))
                .addInput(tensor("x", "batch", 3))
                .addOutput(tensor("y", "batch", 3));
        var constant = Onnx.GraphProto.newBuilder()
                .addNode(Onnx.NodeProto.newBuilder().setOpType("Constant").addOutput("y")
                                 .addAttribute(Onnx.AttributeProto.newBuilder().setName("value")
                                                       .setType(Onnx.AttributeProto.AttributeType.TENSOR)
                                                       .setT(Onnx.TensorProto.newBuilder().setDataType(1).addDims(1)
                                                                     .addFloatData(1))))
                .addOutput(tensor("y", "batch"));
        return List.of(new Case("reduced_output", model(reduce, 8, 13), false),
                       new Case("no_inputs", model(constant, 8, 13), false));
    }

    /**
     * A cross-encoder that scores a tokenized pair, as global-phase reranking uses one.
     * It takes input_ids and attention_mask and returns one logit per pair: the masked sum of token embeddings,
     * projected to a score.
     * With a batch dimension, the shapes are [batch, sequence] -> [batch, 1].
     * Without one, they are [1, 8] -> [1, 1].
     */
    public static Onnx.ModelProto crossEncoder(boolean withBatchDimension) {
        Object[] tokens = withBatchDimension ? new Object[] {"batch", "sequence"} : new Object[] {1, 8};
        Object[] logits = withBatchDimension ? new Object[] {"batch", 1} : new Object[] {1, 1};
        var embeddings = Onnx.TensorProto.newBuilder().setName("embeddings").setDataType(1).addDims(10).addDims(2);
        for (int i = 0; i < 20; i++) embeddings.addFloatData(i * 0.1f);
        var graph = Onnx.GraphProto.newBuilder()
                .addNode(node("Gather", "embeddings", "input_ids", "token_embeddings"))
                .addNode(Onnx.NodeProto.newBuilder().setOpType("Cast").addInput("attention_mask").addOutput("mask")
                                 .addAttribute(Onnx.AttributeProto.newBuilder().setName("to").setI(1)
                                                       .setType(Onnx.AttributeProto.AttributeType.INT)))
                .addNode(node("Unsqueeze", "mask", "last_axis", "mask_3d"))
                .addNode(node("Mul", "token_embeddings", "mask_3d", "masked"))
                .addNode(Onnx.NodeProto.newBuilder().setOpType("ReduceSum").addInput("masked").addInput("token_axis")
                                 .addOutput("pooled")
                                 .addAttribute(Onnx.AttributeProto.newBuilder().setName("keepdims").setI(0)
                                                       .setType(Onnx.AttributeProto.AttributeType.INT)))
                .addNode(node("MatMul", "pooled", "projection", "logits"))
                .addInitializer(embeddings)
                .addInitializer(Onnx.TensorProto.newBuilder().setName("last_axis").setDataType(7).addDims(1).addInt64Data(2))
                .addInitializer(Onnx.TensorProto.newBuilder().setName("token_axis").setDataType(7).addDims(1).addInt64Data(1))
                .addInitializer(Onnx.TensorProto.newBuilder().setName("projection").setDataType(1).addDims(2).addDims(1)
                                        .addFloatData(1).addFloatData(-1))
                .addInput(tensorOfType("input_ids", 7, tokens))
                .addInput(tensorOfType("attention_mask", 7, tokens))
                .addOutput(tensorOfType("logits", 1, logits));
        return model(graph, 8, 13);
    }

    private static Onnx.NodeProto node(String opType, String input1, String input2, String output) {
        return Onnx.NodeProto.newBuilder().setOpType(opType).addInput(input1).addInput(input2).addOutput(output).build();
    }

    private static Onnx.ModelProto matMulWithInitializerAsInput(int irVersion, int opset) {
        var graph = Onnx.GraphProto.newBuilder()
                .addNode(Onnx.NodeProto.newBuilder().setOpType("MatMul").addInput("x").addInput("w").addOutput("y"))
                .addInput(tensor("x", "batch", 2))
                .addInput(tensor("w", 2, 1))
                .addInitializer(Onnx.TensorProto.newBuilder().setName("w").setDataType(1).addDims(2).addDims(1)
                                        .addFloatData(1).addFloatData(2))
                .addOutput(tensor("y", "batch", 1));
        return model(graph, irVersion, opset);
    }

    private static Onnx.ModelProto identity(Onnx.ValueInfoProto input, Onnx.ValueInfoProto output) {
        var graph = Onnx.GraphProto.newBuilder()
                .addNode(Onnx.NodeProto.newBuilder().setOpType("Identity").addInput(input.getName()).addOutput(output.getName()))
                .addInput(input)
                .addOutput(output);
        return model(graph, 8, 13);
    }

    private static Onnx.ModelProto model(Onnx.GraphProto.Builder graph, int irVersion, int opset) {
        return Onnx.ModelProto.newBuilder()
                .setIrVersion(irVersion)
                .addOpsetImport(Onnx.OperatorSetIdProto.newBuilder().setVersion(opset))
                .setGraph(graph.setName("graph"))
                .build();
    }

    // Each argument gives a dimension: a size, a parameter name, or null for neither.
    // No arguments give a scalar.
    private static Onnx.ValueInfoProto tensor(String name, Object... dimensions) {
        return tensorOfType(name, 1, dimensions);
    }

    private static Onnx.ValueInfoProto tensorOfType(String name, int elementType, Object... dimensions) {
        var shape = Onnx.TensorShapeProto.newBuilder();
        for (var dimension : dimensions) {
            var dim = Onnx.TensorShapeProto.Dimension.newBuilder();
            if (dimension instanceof Integer size) dim.setDimValue(size);
            else if (dimension instanceof String param) dim.setDimParam(param);
            shape.addDim(dim);
        }
        return Onnx.ValueInfoProto.newBuilder().setName(name).setType(Onnx.TypeProto.newBuilder()
                .setTensorType(Onnx.TypeProto.Tensor.newBuilder().setElemType(elementType).setShape(shape))).build();
    }

    private static Onnx.ValueInfoProto unshaped(String name) {
        return Onnx.ValueInfoProto.newBuilder().setName(name).setType(Onnx.TypeProto.newBuilder()
                .setTensorType(Onnx.TypeProto.Tensor.newBuilder().setElemType(1))).build();
    }
}
