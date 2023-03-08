// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.rule.SerializationContext;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * make it possible to evaluate an ONNX model anywhere in the ranking expression tree
 */
class OnnxExpressionNode extends CompositeNode {
    private final OnnxModel model;
    private final String onnxOutputName;
    private final TensorType expectedType;
    private final String outputAs;
    private final List<String> modelInputs = new ArrayList<>();
    private final List<ExpressionNode> inputRefs = new ArrayList<>();

    OnnxExpressionNode(OnnxModel model, String onnxOutputName, TensorType expectedType, String outputAs) {
        this.model = model;
        this.onnxOutputName = onnxOutputName;
        this.expectedType = expectedType;
        this.outputAs = outputAs;
        for (var input : model.inputSpecs) {
            modelInputs.add(input.onnxName);
            var optRef = parseOnnxInput(input.source);
            if (optRef.isEmpty()) {
                throw new IllegalArgumentException("Bad input source for ONNX model " + model.name() + ": '" + input + "'");
            }
            var ref = optRef.get();
            inputRefs.add(new ReferenceNode(ref));
        }
    }

    static Optional<Reference> parseOnnxInput(String input) {
        var optRef = Reference.simple(input);
        if (optRef.isPresent()) {
            return optRef;
        }
        try {
            var ref = Reference.fromIdentifier(input);
            return Optional.of(ref);
        } catch (Exception e) {
            // fallthrough
        }
        return Optional.empty();
    }

    @Override
    public List<ExpressionNode> children() { return List.copyOf(inputRefs); }

    @Override
    public CompositeNode setChildren(List<ExpressionNode> children) {
        if (inputRefs.size() != children.size()) {
            throw new IllegalArgumentException("bad setChildren");
        }
        inputRefs.clear();
        inputRefs.addAll(children);
        return this;
    }

    @Override
    public Value evaluate(Context context) {
        Map<String, Tensor> inputs = new HashMap<>();
        for (int i = 0; i < modelInputs.size(); i++) {
            Value inputValue = inputRefs.get(i).evaluate(context);
            inputs.put(modelInputs.get(i), inputValue.asTensor());
        }
        return new TensorValue(model.unmappedEvaluate(inputs, onnxOutputName));
    }

    @Override
    public TensorType type(TypeContext<Reference> context) { return expectedType; }

    @Override
    public int hashCode() { return Objects.hash("OnnxExpressionNode", model.name(), onnxOutputName); }

    @Override
    public StringBuilder toString(StringBuilder b, SerializationContext context, Deque<String> path, CompositeNode parent) {
        b.append("onnx_expression_node(").append(model.name()).append(")");
        if (outputAs != null && ! outputAs.equals("")) {
            b.append(".").append(outputAs);
        }
        return b;
    }
}
