// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode;
import com.yahoo.tensor.functions.DynamicTensor;
import com.yahoo.tensor.functions.Generate;
import com.yahoo.tensor.functions.Slice;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.yahoo.searchlib.rankingexpression.Reference.RANKING_EXPRESSION_WRAPPER;

/**
 * extract information about needed bindings, arguments, and onnx models from expression functions
 */
class BindingExtractor {

    private final Map<FunctionReference, ExpressionFunction> referencedFunctions;
    private final List<OnnxModel> onnxModels;

    public BindingExtractor(Map<FunctionReference, ExpressionFunction> referencedFunctions, List<OnnxModel> onnxModels) {
        this.referencedFunctions = referencedFunctions;
        this.onnxModels = onnxModels;
    }

    static class FunctionInfo {
        /** The names which may be bound externally */
        final Set<String> bindTargets = new LinkedHashSet<>();

        /** The names which needs to be bound externally, subset of the above */
        final Set<String> arguments = new LinkedHashSet<>();

        /** ONNX models in use */
        final Map<String, OnnxModel> onnxModelsInUse = new LinkedHashMap<>();

        void merge(FunctionInfo other) {
            bindTargets.addAll(other.bindTargets);
            arguments.addAll(other.arguments);
            onnxModelsInUse.putAll(other.onnxModelsInUse);
        }

        void removeTarget(String name) {
            bindTargets.remove(name);
            arguments.remove(name);
        }
    }

    private final Map<FunctionReference, FunctionInfo> functionsInfo = new LinkedHashMap<>();

    FunctionInfo extractFrom(FunctionReference ref) {
        if (functionsInfo.containsKey(ref))
            return functionsInfo.get(ref);
        ExpressionFunction function = referencedFunctions.get(ref);
        FunctionInfo result = extractFrom(function);
        functionsInfo.put(ref, result);
        return result;
    }

    FunctionInfo extractFrom(ExpressionFunction function) {
        if (function == null)
            return null;
        ExpressionNode functionNode = function.getBody().getRoot();
        return extractBindTargets(functionNode);
    }

    private FunctionInfo extractBindTargets(ExpressionNode node) {
        var result = new FunctionInfo();
        if (isFunctionReference(node)) {
            var opt = FunctionReference.fromSerial(node.toString());
            if (opt.isEmpty()) {
                throw new IllegalArgumentException("Could not extract function " + node + " from serialized form '" + node.toString() +"'");
            }
            FunctionReference reference = opt.get();
            result.bindTargets.add(reference.serialForm());
            FunctionInfo subInfo = extractFrom(reference);
            if (subInfo == null) {
                // not available, must be supplied as input
                result.arguments.add(reference.serialForm());
            } else {
                result.merge(subInfo);
            }
            return result;
        }
        else if (node instanceof TensorFunctionNode tfn) {
            for (ExpressionNode child : tfn.children()) {
                result.merge(extractBindTargets(child));
            }
            // ignore return value:
            tfn.withTransformedExpressions(expr -> {
                    result.merge(extractBindTargets(expr));
                    return expr;
                });
            var f = tfn.function();
            if (f instanceof Generate) {
                var tt = f.type(null);
                for (var dim : tt.dimensions()) {
                    result.removeTarget(dim.name());
                }
            }
            return result;
        }
        else if (isOnnx(node)) {
            return extractOnnxTargets(node);
        }
        else if (isConstant(node)) {
            result.bindTargets.add(node.toString());
            return result;
        }
        else if (node instanceof ReferenceNode) {
            result.bindTargets.add(node.toString());
            result.arguments.add(node.toString());
            return result;
        }
        else if (node instanceof CompositeNode cNode) {
            for (ExpressionNode child : cNode.children()) {
                result.merge(extractBindTargets(child));
            }
            return result;
        }
        if (node instanceof ConstantNode) {
            return result;
        }
        // TODO check if more node types need consideration here
        return result;
    }

    /**
     * Extract the feature used to evaluate the onnx model. e.g. onnx(name) and add
     * that as a bind target and argument. During evaluation, this will be evaluated before
     * the rest of the expression and the result is added to the context. Also extract the
     * inputs to the model and add them as bind targets and arguments.
     */
    private FunctionInfo extractOnnxTargets(ExpressionNode node) {
        var result = new FunctionInfo();
        String onnxFeature = node.toString();
        result.bindTargets.add(onnxFeature);
        Optional<String> modelName = getArgument(node);
        if (modelName.isPresent()) {
            for (OnnxModel onnxModel : onnxModels) {
                if (onnxModel.name().equals(modelName.get())) {
                    // Load the model (if not already loaded) to extract inputs
                    onnxModel.load();
                    for(String input : onnxModel.inputs().keySet()) {
                        result.bindTargets.add(input);
                        result.arguments.add(input);
                    }
                    result.onnxModelsInUse.put(onnxFeature, onnxModel);
                    return result;
                }
            }
        }
        // not found, must be supplied as argument
        result.arguments.add(onnxFeature);
        return result;
    }

    private Optional<String> getArgument(ExpressionNode node) {
        if (node instanceof ReferenceNode reference) {
            if (reference.getArguments().size() > 0) {
                var arg = reference.getArguments().expressions().get(0);
                if (arg instanceof ConstantNode) {
                    return Optional.of(stripQuotes(arg.toString()));
                }
                if (arg instanceof ReferenceNode refNode) {
                    return Optional.of(refNode.getName());
                }
            }
        }
        return Optional.empty();
    }

    public static String stripQuotes(String s) {
        if (s.length() < 3) {
            return s;
        }
        int lastIdx = s.length() - 1;
        char first = s.charAt(0);
        char last = s.charAt(lastIdx);
        if (first == '"'  &&  last == '"')  return s.substring(1, lastIdx);
        if (first == '\'' && last == '\'') return s.substring(1, lastIdx);
        return s;
    }

    private boolean isFunctionReference(ExpressionNode node) {
        if ( ! (node instanceof ReferenceNode reference)) return false;
        return reference.getName().equals(RANKING_EXPRESSION_WRAPPER) && reference.getArguments().size() == 1;
    }

    private boolean isOnnx(ExpressionNode node) {
        if ( ! (node instanceof ReferenceNode reference)) return false;
        return reference.getName().equals("onnx") || reference.getName().equals("onnxModel");
    }

    private boolean isConstant(ExpressionNode node) {
        if ( ! (node instanceof ReferenceNode reference)) return false;
        return reference.getName().equals("constant") && reference.getArguments().size() == 1;
    }

}
