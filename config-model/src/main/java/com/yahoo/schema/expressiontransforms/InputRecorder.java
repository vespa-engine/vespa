// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.expressiontransforms;

import com.yahoo.schema.RankProfile;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.transform.ExpressionTransformer;

import java.util.Set;

/**
 * Analyzes expression to figure out what inputs it needs
 *
 * @author arnej
 */
public class InputRecorder extends ExpressionTransformer<RankProfileTransformContext> {

    private final Set<String> neededInputs;

    public InputRecorder(Set<String> target) {
        this.neededInputs = target;
    }

    @Override
    public ExpressionNode transform(ExpressionNode node, RankProfileTransformContext context) {
        if (node instanceof ReferenceNode r) {
            handle(r, context);
            return node;
        }
        if (node instanceof CompositeNode c)
            return transformChildren(c, context);
        if (node instanceof ConstantNode) {
            return node;
        }
        throw new IllegalArgumentException("Cannot handle node type: "+ node + " [" + node.getClass() + "]");
    }

    private void handle(ReferenceNode feature, RankProfileTransformContext context) {
        String name = feature.getName();
        var args = feature.getArguments();
        if (args.size() == 0) {
            var f = context.rankProfile().getFunctions().get(name);
            if (f != null && f.function().arguments().size() == 0) {
                transform(f.function().getBody().getRoot(), context);
                return;
            }
            neededInputs.add(feature.toString());
            return;
        }
        if (args.size() == 1) {
            if ("value".equals(name)) {
                transform(args.expressions().get(0), context);
                return;
            }
            if ("attribute".equals(name) || "query".equals(name)) {
                neededInputs.add(feature.toString());
                return;
            }
            if ("constant".equals(name)) {
                var allConstants = context.rankProfile().constants();
                if (allConstants.containsKey(feature.reference())) {
                    neededInputs.add(feature.toString());
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
            for (String onnxInput : model.getInputMap().values()) {
                neededInputs.add(onnxInput);
            }
            return;
        }
        throw new IllegalArgumentException("cannot handle feature: " + feature);
    }
}
