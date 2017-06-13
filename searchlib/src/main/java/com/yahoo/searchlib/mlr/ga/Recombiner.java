// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.mlr.ga;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.rule.*;

import java.util.*;
import java.util.logging.Logger;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * A class which returns a mutated, recombined genome from a list of parent genomes.
 *
 * @author bratseth
 */
public class Recombiner {

    // TODO: Either make ranking expressions immutable and get rid of parent pointer, or do clone everywhere below

    private static final Logger log = Logger.getLogger(Trainer.class.getName());

    private final Random random = new Random();

    private final List<String> features;

    private final TrainingParameters parameters;

    /**
     * Creates a recombiner
     *
     * @param features the list of feature names which are possible within the space we are training,
     *                 such that these may be spontaneously added to expressions.
     */
    public Recombiner(Collection<String> features, TrainingParameters trainingParameters) {
        this.features = Collections.unmodifiableList(new ArrayList<>(features));
        this.parameters = trainingParameters;
    }

    public RankingExpression recombine(RankingExpression genome, List<RankingExpression> genePool) {
        List<ExpressionNode> genePoolRoots = new ArrayList<>();
        for (RankingExpression genePoolGenome : genePool)
            genePoolRoots.add(genePoolGenome.getRoot());
        return new RankingExpression(mutate(genome.getRoot(), genePoolRoots, 0));
    }

    private ExpressionNode mutate(ExpressionNode gene, List<ExpressionNode> genePool, int depth) {
        // TODO: Extract insert level
        if (gene instanceof BooleanNode)
            return simplifyCondition(mutateChildren((CompositeNode)gene,genePool,depth+1));
        if (gene instanceof CompositeNode)
            return insertNodeLevel(simplify(removeNodeLevel(mutateChildren((CompositeNode)gene,genePool,depth+1))), genePool, depth+1);
        else
            return insertNodeLevel(mutateLeaf(gene), genePool, depth+1);
    }

    private BooleanNode simplifyCondition(ExpressionNode node) {
        // Nothing yet
        return (BooleanNode)node;
    }

    /** Very basic algorithmic simplification */
    private ExpressionNode simplify(ExpressionNode node) {
        if (! (node instanceof CompositeNode)) return node;
        CompositeNode composite = (CompositeNode)node;
        if (maxDepth(composite)>2) return composite;
        List<ExpressionNode> children = composite.children();
        if (children.size()!=2) return composite;
        if ( ! (children.get(0) instanceof ConstantNode)) return composite;
        if ( ! (children.get(1) instanceof ConstantNode)) return composite;
        return new ConstantNode(composite.evaluate(null));
    }

    private CompositeNode mutateChildren(CompositeNode gene, List<ExpressionNode> genePool, int depth) {
        if (gene instanceof ReferenceNode) return gene; // TODO: Remove if we make this a non-composite

        List<ExpressionNode> mutatedChildren = new ArrayList<>();
        for (ExpressionNode child : gene.children())
            mutatedChildren.add(mutate(child, genePool, depth));
        return gene.setChildren(mutatedChildren);
    }

    private ExpressionNode insertNodeLevel(ExpressionNode gene, List<ExpressionNode> genePool, int depth) {
        if (probability() < 0.9) return gene;
        if (depth + maxDepth(gene) >= parameters.getMaxExpressionDepth()) return gene;
        ExpressionNode newChild = generateChild(genePool, depth);
        if (probability() < 0.5)
            return generateComposite(gene, newChild, genePool, depth);
        else
            return generateComposite(newChild, gene, genePool, depth);
    }

    private ExpressionNode removeNodeLevel(CompositeNode gene) {
        if (gene instanceof ReferenceNode) return gene; // TODO: Remove if we make featurenode a non-composite
        if (probability() < 0.9) return gene;
        return randomFrom(gene.children());
    }

    private ExpressionNode generateComposite(ExpressionNode left, ExpressionNode right, List<ExpressionNode> genePool, int depth) {
        int type = random.nextInt(2 + ( parameters.getAllowConditions() ? 1:0 ) ); // pick equally between 2 or 3 types
        if (type == 0) {
            return new ArithmeticNode(left, pickArithmeticOperator(), right);
        }
        else if (type == 1) {
            Function function = pickFunction();
            if (function.arity() == 1)
                return new FunctionNode(function, left);
            else // arity==2
                return new FunctionNode(function, left, right);
        }
        else {
            return new IfNode(generateCondition(genePool, depth + 1), left, right);
        }
    }

    private BooleanNode generateCondition(List<ExpressionNode> genePool, int depth) {
        // TODO: Add set membership nodes
        return new ComparisonNode(generateChild(genePool, depth), TruthOperator.SMALLER, generateChild(genePool, depth));
    }

    private ExpressionNode generateChild(List<ExpressionNode> genePool, int depth) {
        if (genePool.isEmpty() || probability() < 0.1) { // entirely new child
            return generateLeaf();
        }
        else { // pick from gene pool
            ExpressionNode picked = randomFrom(genePool);
            int pickedDepth = 0;
            // descend until we are at at least the same depth as this depth
            // to make sure branches spliced in are shallow enough that we avoid growing
            // larger than maxDepth
            while (picked instanceof CompositeNode && (pickedDepth++ < depth || probability() < 0.5)) {
                if (picked instanceof ReferenceNode) continue; // TODO: Remove if we make referencenode a noncomposite
                picked = randomFrom(((CompositeNode)picked).children());
            }
            return picked;
        }
    }

    public ExpressionNode mutateLeaf(ExpressionNode leaf) {
        if (probability() < 0.5) return leaf; // TODO: For performance. Drop?
        // TODO: Other leaves
        ConstantNode constant = (ConstantNode)leaf;
        return new ConstantNode(DoubleValue.frozen(constant.getValue().asDouble()*aboutOne()));
    }

    public ExpressionNode generateLeaf() {
        if (probability()<0.5 || features.size() == 0)
            return new ConstantNode(DoubleValue.frozen(random.nextDouble() * 2000 - 1000)); // TODO: Use some non-uniform distribution
        else
            return new ReferenceNode(randomFrom(features));
    }

    private double aboutOne() {
        return 1 + Math.pow(-0.1, random.nextInt(4) + 1);
    }

    private double probability() {
        return random.nextDouble();
    }

    private <T> T randomFrom(List<T> expressionList) {
        return expressionList.get(random.nextInt(expressionList.size()));
    }

    private ArithmeticOperator pickArithmeticOperator() {
        switch (random.nextInt(4)) {
            case 0: return ArithmeticOperator.PLUS;
            case 1: return ArithmeticOperator.MINUS;
            case 2: return ArithmeticOperator.MULTIPLY;
            case 3: return ArithmeticOperator.DIVIDE;
        }
        throw new RuntimeException("This cannot happen");
    }

    /** Pick among the subset of functions which are probably useful */
    private Function pickFunction() {
        switch (random.nextInt(5)) {
            case 0: return Function.tanh;
            case 1: return Function.exp;
            case 2: return Function.log;
            case 3: return Function.pow;
            case 4: return Function.sqrt;
        }
        throw new RuntimeException("This cannot happen");
    }

    // TODO: Make ranking expressions immutable and compute this on creation?
    private int maxDepth(ExpressionNode node) {
        if ( ! (node instanceof CompositeNode)) return 1;

        int maxChildDepth = 0;
        for (ExpressionNode child : ((CompositeNode)node).children())
            maxChildDepth = Math.max(maxDepth(child), maxChildDepth);
        return maxChildDepth + 1;
    }

}
