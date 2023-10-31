// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.expressiontransforms;

import com.yahoo.schema.FeatureNames;
import com.yahoo.schema.RankProfile.RankFeatureNormalizer;
import com.yahoo.searchlib.rankingexpression.evaluation.BooleanValue;
import com.yahoo.searchlib.rankingexpression.rule.OperationNode;
import com.yahoo.searchlib.rankingexpression.rule.Operator;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.IfNode;
import com.yahoo.searchlib.rankingexpression.transform.ExpressionTransformer;
import com.yahoo.searchlib.rankingexpression.transform.TransformContext;
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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

/**
 * Recognizes pseudo-functions and creates global-phase normalizers
 * @author arnej
 */
public class NormalizerFunctionExpander extends ExpressionTransformer<RankProfileTransformContext> {

    public final static String NORMALIZE_LINEAR = "normalize_linear";
    public final static String RECIPROCAL_RANK = "reciprocal_rank";
    public final static String RECIPROCAL_RANK_FUSION = "reciprocal_rank_fusion";

    @Override
    public ExpressionNode transform(ExpressionNode node, RankProfileTransformContext context) {
        if (node instanceof ReferenceNode r) {
            node = transformReference(r, context);
        }
        if (node instanceof CompositeNode composite) {
            node = transformChildren(composite, context);
        }
        return node;
    }

    private ExpressionNode transformReference(ReferenceNode node, RankProfileTransformContext context) {
        Reference ref = node.reference();
        String name = ref.name();
        if (ref.output() != null) {
            return node;
        }
        var f = context.rankProfile().getFunctions().get(name);
        if (f != null) {
            // never transform declared functions
            return node;
        }
        return switch(name) {
            case RECIPROCAL_RANK_FUSION -> transform(expandRRF(ref), context);
            case NORMALIZE_LINEAR -> transformNormLin(ref, context);
            case RECIPROCAL_RANK -> transformRRank(ref, context);
            default -> node;
        };
    }

    private ExpressionNode expandRRF(Reference ref) {
        var args = ref.arguments();
        if (args.size() < 2) {
            throw new IllegalArgumentException("must have at least 2 arguments: " + ref);
        }
        List<ExpressionNode> children = new ArrayList<>();
        List<Operator> operators = new ArrayList<>();
        for (var arg : args.expressions()) {
            if (! children.isEmpty()) operators.add(Operator.plus);
            children.add(new ReferenceNode(RECIPROCAL_RANK, List.of(arg), null));
        }
        // must be further transformed (see above)
        return new OperationNode(children, operators);
    }

    private ExpressionNode transformNormLin(Reference ref, RankProfileTransformContext context) {
        var args = ref.arguments();
        if (args.size() != 1) {
            throw new IllegalArgumentException("must have exactly 1 argument: " + ref);
        }
        var input = args.expressions().get(0);
        if (input instanceof ReferenceNode inputRefNode) {
            var inputRef = inputRefNode.reference();
            RankFeatureNormalizer normalizer = RankFeatureNormalizer.linear(ref, inputRef);
            context.rankProfile().addFeatureNormalizer(normalizer);
            var newRef = Reference.fromIdentifier(normalizer.name());
            return new ReferenceNode(newRef);
        } else {
            throw new IllegalArgumentException("the first argument must be a simple feature: " + ref + " => " + input.getClass());
        }
    }

    private ExpressionNode transformRRank(Reference ref, RankProfileTransformContext context) {
        var args = ref.arguments();
        if (args.size() < 1 || args.size() > 2) {
            throw new IllegalArgumentException("must have 1 or 2 arguments: " + ref);
        }
        double k = 60.0;
        if (args.size() == 2) {
            var kArg = args.expressions().get(1);
            if (kArg instanceof ConstantNode kNode) {
                k = kNode.getValue().asDouble();
            } else {
                throw new IllegalArgumentException("the second argument (k) must be a constant in: " + ref);
            }
        }
        var input = args.expressions().get(0);
        if (input instanceof ReferenceNode inputRefNode) {
            var inputRef = inputRefNode.reference();
            RankFeatureNormalizer normalizer = RankFeatureNormalizer.rrank(ref, inputRef, k);
            context.rankProfile().addFeatureNormalizer(normalizer);
            var newRef = Reference.fromIdentifier(normalizer.name());
            return new ReferenceNode(newRef);
        } else {
            throw new IllegalArgumentException("the first argument must be a simple feature: " + ref);
        }
    }
}
