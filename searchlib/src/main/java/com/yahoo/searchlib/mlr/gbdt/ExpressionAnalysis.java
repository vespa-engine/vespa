// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.mlr.gbdt;

import com.yahoo.searchlib.rankingexpression.rule.SetMembershipNode;
import com.yahoo.yolean.Exceptions;
import com.yahoo.searchlib.mlr.ga.Individual;
import com.yahoo.searchlib.mlr.ga.PrintingTracker;
import com.yahoo.searchlib.mlr.ga.RankingExpressionCaseList;
import com.yahoo.searchlib.mlr.ga.Trainer;
import com.yahoo.searchlib.mlr.ga.TrainingParameters;
import com.yahoo.searchlib.mlr.ga.TrainingSet;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.searchlib.rankingexpression.rule.Arguments;
import com.yahoo.searchlib.rankingexpression.rule.ArithmeticNode;
import com.yahoo.searchlib.rankingexpression.rule.ComparisonNode;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.rule.IfNode;
import com.yahoo.searchlib.rankingexpression.rule.NegativeNode;
import com.yahoo.searchlib.rankingexpression.rule.TruthOperator;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A standalone tool which analyzes a GBDT form ranking expression
 *
 * @author bratseth
 */
public class ExpressionAnalysis {

    private final Map<String, Feature> features = new HashMap<>();

    private int currentTree;

    private final RankingExpression expression;

    public ExpressionAnalysis(RankingExpression expression) {
        this.expression = expression;
        if ( ! instanceOf(expression.getRoot(), ArithmeticNode.class)) return;
        analyzeSum((ArithmeticNode)expression.getRoot());
    }

    /** Returns the expression analyzed by this */
    public RankingExpression expression() { return expression; }

    /** Returns the analysis of each feature in this expression as a read-only map indexed by feature name */
    private Map<String, Feature> featureMap() {
        return Collections.unmodifiableMap(features);
    }

    /** Returns list containing the analysis of each feature, sorted by decreasing usage */
    private List<Feature> features() {
        List<Feature> featureList = new ArrayList<>(features.values());
        Collections.sort(featureList);
        return featureList;
    }

    /** Returns the name of each feature, sorted by decreasing usage */
    private List<String> featureNames() {
        List<String> featureNameList = new ArrayList<>(features.values().size());
        for (Feature feature : features())
            featureNameList.add(feature.name());
        return featureNameList;
    }

    private void analyzeSum(ArithmeticNode node) {
        for (ExpressionNode child : node.children()) {
            currentTree++;
            analyze(child);
        }
    }

    private void analyze(ExpressionNode node) {
        if (node instanceof IfNode) {
            analyzeIf((IfNode)node);
        }

        if (node instanceof CompositeNode) {
            for (ExpressionNode child : ((CompositeNode)node).children())
                analyze(child);
        }
    }

    private void analyzeIf(IfNode node) {
        if (node.getCondition() instanceof ComparisonNode)
            analyzeComparisonIf(node);
        else if (node.getCondition() instanceof SetMembershipNode)
            analyzeSetMembershipIf(node);
        else
            System.err.println("Warning: Expected a comparison or set membership test, got " + node.getCondition().getClass());
    }

    private void analyzeComparisonIf(IfNode node) {
        ComparisonNode comparison = (ComparisonNode)node.getCondition();

        if (comparison.getOperator() != TruthOperator.SMALLER) {
            System.err.println("Warning: This expression has " + comparison.getOperator() + " where we expect < :" +
                               comparison);
            return;
        }

        if ( ! instanceOf(comparison.getLeftCondition(), ReferenceNode.class)) return;
        String featureName = ((ReferenceNode)comparison.getLeftCondition()).getName();

        Double value = nodeValue(comparison.getRightCondition());
        if (value == null) return;

        ComparisonFeature feature = (ComparisonFeature)features.get(featureName);
        if (feature == null) {
            feature = new ComparisonFeature(featureName);
            features.put(featureName, feature);
        }
        feature.isComparedTo(value, currentTree, average(node.getTrueExpression()), average(node.getFalseExpression()));
    }

    private void analyzeSetMembershipIf(IfNode node) {
        SetMembershipNode membershipTest = (SetMembershipNode)node.getCondition();

        if ( ! instanceOf(membershipTest.getTestValue(), ReferenceNode.class)) return;
        String featureName = ((ReferenceNode)membershipTest.getTestValue()).getName();

        SetMembershipFeature feature = (SetMembershipFeature)features.get(featureName);
        if (feature == null) {
            feature = new SetMembershipFeature(featureName);
            features.put(featureName, feature);
        }
    }

    /**
     * Returns the value of a constant node, or a negative wrapping a constant.
     * Warns and returns null if it is neither.
     */
    private Double nodeValue(ExpressionNode node) {
        if (node instanceof NegativeNode) {
            NegativeNode negativeNode = (NegativeNode)node;
            if ( ! instanceOf(negativeNode.getValue(), ConstantNode.class)) return null;
            return - ((ConstantNode)negativeNode.getValue()).getValue().asDouble();
        }
        else {
            if ( ! instanceOf(node, ConstantNode.class)) return null;
            return ((ConstantNode)node).getValue().asDouble();
        }
    }


    /** Returns the average value of all the leaf constants below this */
    private double average(ExpressionNode node) {
        Sum sum = new Sum();
        average(node, sum);
        return sum.average();
    }

    private void average(ExpressionNode node, Sum sum) {
        if (node instanceof CompositeNode) {
            for (ExpressionNode child : ((CompositeNode)node).children())
                average(child, sum);
        }
        else {
            Double value = nodeValue(node);
            if (value == null) return;
            sum.add(value);
        }
    }

    private boolean instanceOf(Object object, Class<?> clazz) {
        if (clazz.isAssignableFrom(object.getClass())) return true;
        System.err.println("Warning: This expression has " + object.getClass() + " where we expect " + clazz +
                           ": Instance " + object);
        return false;
    }

    private List<Context> generateArgumentSets(int count) {
        List<Context> argumentSets = new ArrayList<>(count);
        for (int i=0; i<count; i++) {
            ArgumentIgnoringMapContext context = new ArgumentIgnoringMapContext();
            for (Feature feature : features()) {
                if (feature instanceof ComparisonFeature) {
                    ComparisonFeature comparison = (ComparisonFeature)feature;
                    context.put(comparison.name(),randomBetween(comparison.lowerBound(), comparison.upperBound()));
                }
                // TODO: else if (feature instanceof SetMembershipFeature)
            }
            argumentSets.add(context);
        }
        return argumentSets;
    }

    private Random random = new Random();
    /** Returns a random value in [lowerBound, upperBound&gt; */
    private double randomBetween(double lowerBound, double upperBound) {
        return random.nextDouble()*(upperBound-lowerBound)+lowerBound;
    }

    private static class ArgumentIgnoringMapContext extends MapContext {

        @Override
        public Value get(String name, Arguments arguments,String output) {
            return super.get(name, null, output);
        }

    }

    /** Generates a textual report from analyzing this expression */
    public String report() {
        StringBuilder b = new StringBuilder();
        b.append("Trees: " + currentTree).append("\n");
        b.append("Features:\n");
        for (Feature feature : features())
            b.append("  " + feature).append("\n");
        return b.toString();
    }

    private static final String usage = "\nUsage: ExpressionAnalysis [myExpressionFile.expression]";

    public static void main(String[] args) {
        if (args.length == 0) error("No arguments." + usage);

        ExpressionAnalysis analysis = analysisFromFile(args[0]);

        if (1==1) return; // Turn off ga training
        if (args.length == 1) {
            new GATraining(analysis);
        }
        else if (args.length == 2) {
            try {
                new LearntExpressionAnalysis(analysis, new RankingExpression(args[1]));
            }
            catch (ParseException e) {
                error("Syntax error in argument expression: " + Exceptions.toMessageString(e));
            }
        }
        else {
            error("Unexpectedly got more than 2 arguments." + usage);
        }

    }

    private static ExpressionAnalysis analysisFromFile(String fileName) {
        try (Reader fileReader = new BufferedReader(new FileReader(fileName))) {
            System.out.println("Analyzing " + fileName + "...");
            ExpressionAnalysis analysis = new ExpressionAnalysis(new RankingExpression(fileReader));
            System.out.println(analysis.report());
            return analysis;
        }
        catch (FileNotFoundException e) {
            error("Could not find '" + fileName + "'");
        }
        catch (IOException e) {
            error("Failed reading '" + fileName + "': " + Exceptions.toMessageString(e));
        }
        catch (ParseException e) {
            error("Syntax error in '" + fileName + "': " + Exceptions.toMessageString(e));
        }
        return null;
    }

    private static class LearntExpressionAnalysis {

        public LearntExpressionAnalysis(ExpressionAnalysis analysis, RankingExpression learntExpression) {
            int cases = 1000;
            TrainingSet newTrainingSet = new TrainingSet(new RankingExpressionCaseList(analysis.generateArgumentSets(cases),
                                                                                       analysis.expression()), new TrainingParameters());
            Individual winner = new Individual(learntExpression, newTrainingSet);
            System.out.println("With separate training set: " + winner.toShortString() + " (" + winner.calculateAverageError() + ")");
        }

    }

    private static class GATraining {

        public GATraining(ExpressionAnalysis analysis) {
            int skipFeatures = 0;
            int featureCount = analysis.featureNames().size();
            int cases = 1000;
            TrainingParameters parameters = new TrainingParameters();
            parameters.setInitialSpeciesSize(50);
            parameters.setSpeciesLifespan(50);
            //parameters.setAllowConditions(false); // disallow non-smooth functions
            parameters.setMaxExpressionDepth(8);
            TrainingSet trainingSet = new TrainingSet(new RankingExpressionCaseList(analysis.generateArgumentSets(cases),
                                                                                    analysis.expression()), parameters);
            Trainer trainer = new Trainer(trainingSet, new HashSet<>(analysis.featureNames().subList(skipFeatures, featureCount)));

            System.out.println("Learning ...");
            RankingExpression learntExpression = trainer.train(parameters, new PrintingTracker(100, 0, 1));
            System.out.println("Learnt expression: " + learntExpression);

            // Check for overtraining
            new LearntExpressionAnalysis(analysis, learntExpression);
        }

    }

    private static void error(String message) {
        System.err.println(message);
        System.exit(1);
    }

    public abstract static class Feature implements Comparable<Feature> {

        private final String name;

        protected Feature(String name) {
            this.name = name;
        }

        public String name() { return name; }

        /** Primary sort by type, secondary by name */
        @Override
        public int compareTo(Feature other) {
            int typeComparison = this.getClass().getName().compareTo(other.getClass().getName());
            if (typeComparison != 0) return typeComparison;
            return this.name.compareTo(other.name);
        }

    }

    /** A feature used in comparisons. These are the ones on which our serious analysis is focused */
    public static class ComparisonFeature extends Feature {

        private double lowerBound = Double.MAX_VALUE;
        private double upperBound = Double.MIN_VALUE;

        /** The number of usages of this feature */
        private int usages = 0;

        /** The sum of the tree numbers where this is accessed */
        private int treeNumberSum = 0;

        /**
         * The net times where the left values are smaller than the right values for this
         * (which is a measure of correlation between input and output because the comparison is &lt;)
         */
        private int correlationCount = 0;

        /**
         * The sum difference in returned value between choosing the right and left branch due to this feature
         */
        private double netSum = 0;

        public ComparisonFeature(String name) {
            super(name);
        }

        public double lowerBound() { return lowerBound; }
        public double upperBound() { return upperBound; }

        public void isComparedTo(double value, int inTreeNumber, double leftAverage, double rightAverage) {
            lowerBound = Math.min(lowerBound, value);
            upperBound = Math.max(upperBound, value);
            usages++;
            treeNumberSum += inTreeNumber;
            correlationCount += leftAverage < rightAverage ? 1 : -1;
            netSum += rightAverage - leftAverage;
        }

        /** Override to do secondary sort by usages */
        public int compareTo(Feature o) {
            if ( ! (o instanceof ComparisonFeature)) return super.compareTo(o);
            ComparisonFeature other = (ComparisonFeature)o;
            return - Integer.compare(this.usages, other.usages);
        }

        @Override
        public String toString() {
            return "Numeric     feature: " + name() +
                    ": range [" + lowerBound + ", " + upperBound + "]" +
                    ", usages " + usages +
                    ", average tree occurrence " + (treeNumberSum / usages) +
                    ", correlation: " + (correlationCount / (double)usages) +
                    ", net contribution: " + netSum;
        }

    }

    /** A feature used in set membership tests */
    public static class SetMembershipFeature extends Feature {

        public SetMembershipFeature(String name) {
            super(name);
        }

        @Override
        public String toString() {
            return "Categorical feature: " + name();
        }

    }

    /** A sum which can returns its average */
    private static class Sum {

        private double sum;
        private int count;

        public void add(double value) {
            sum+=value;
            count++;
        }

        public double average() {
            return sum / count;
        }

    }

}
