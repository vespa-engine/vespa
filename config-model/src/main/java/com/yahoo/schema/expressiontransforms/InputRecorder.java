// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.expressiontransforms;

import com.yahoo.schema.FeatureNames;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode;
import com.yahoo.searchlib.rankingexpression.transform.ExpressionTransformer;
import com.yahoo.tensor.functions.Generate;

import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Analyzes expression to figure out what inputs it needs
 *
 * @author arnej
 */
public class InputRecorder extends ExpressionTransformer<InputRecorderContext> {

    private static final Logger log = Logger.getLogger(InputRecorder.class.getName());

    private final Set<String> neededInputs;
    private final Set<String> handled = new HashSet<>();

    public InputRecorder(Set<String> target) {
        this.neededInputs = target;
    }

    public void process(RankingExpression expression, RankProfileTransformContext context) {
        transform(expression.getRoot(), new InputRecorderContext(context));
    }

    @Override
    public ExpressionNode transform(ExpressionNode node, InputRecorderContext context) {
        if (node instanceof ReferenceNode r) {
            handle(r, context);
            return node;
        }
        if (node instanceof TensorFunctionNode t) {
            var f = t.function();
            if (f instanceof Generate) {
                var childContext = new InputRecorderContext(context);
                var tt = f.type(context.types());
                // expects only indexed dimensions, should we check?
                for (var dim : tt.dimensions()) {
                    childContext.localVariables().add(dim.name());
                }
                return transformChildren(t, childContext);
            }
            node = t.withTransformedExpressions(expr -> transform(expr, context));
        }
        if (node instanceof CompositeNode c)
            return transformChildren(c, context);
        if (node instanceof ConstantNode) {
            return node;
        }
        throw new IllegalArgumentException("Cannot handle node type: "+ node + " [" + node.getClass() + "]");
    }

    private void handle(ReferenceNode feature, InputRecorderContext context) {
        Reference ref = feature.reference();
        String name = ref.name();
        var args = ref.arguments();
        boolean simpleFunctionOrIdentifier = (args.size() == 0) && (ref.output() == null);
        if (simpleFunctionOrIdentifier && context.localVariables().contains(name)) {
            return;
        }
        if (ref.isSimpleRankingExpressionWrapper()) {
            name = ref.simpleArgument().get();
            simpleFunctionOrIdentifier = true;
        }
        if (simpleFunctionOrIdentifier) {
            if (handled.contains(name)) {
                return;
            }
            var f = context.rankProfile().getFunctions().get(name);
            if (f != null && f.function().arguments().size() == 0) {
                transform(f.function().getBody().getRoot(), context);
                handled.add(name);
                return;
            }
            neededInputs.add(feature.toString());
            return;
        }
        if (FeatureNames.isSimpleFeature(ref)) {
            if (FeatureNames.isAttributeFeature(ref)) {
                neededInputs.add(feature.toString());
                return;
            }
            if (FeatureNames.isQueryFeature(ref)) {
                // get rid of this later, we should be able
                // to get it from the query
                neededInputs.add(feature.toString());
                return;
            }
            if (FeatureNames.isConstantFeature(ref)) {
                var allConstants = context.rankProfile().constants();
                if (allConstants.containsKey(ref)) {
                    // assumes we have the constant available during evaluation without any more wiring
                    return;
                }
                throw new IllegalArgumentException("unknown constant: " + feature);
            }
        }
        if ("onnx".equals(name)) {
            if (args.size() != 1) {
                throw new IllegalArgumentException("expected name of ONNX model as argument: " + feature);
            }
            var arg = args.expressions().get(0);
            var models = context.rankProfile().onnxModels();
            var model = models.get(arg.toString());
            if (model == null) {
                throw new IllegalArgumentException("missing onnx model: " + arg);
            }
            model.getInputMap().forEach((__, onnxInput) -> {
                var reader = new StringReader(onnxInput);
                try {
                    var asExpression = new RankingExpression(reader);
                    transform(asExpression.getRoot(), context);
                } catch (ParseException e) {
                    throw new IllegalArgumentException("illegal onnx input '" + onnxInput + "': " + e.getMessage());
                }
            });
            return;
        }
        neededInputs.add(feature.toString());
    }
}
