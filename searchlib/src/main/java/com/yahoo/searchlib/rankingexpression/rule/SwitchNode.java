// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.yahoo.api.annotations.Beta;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * A switch expression which selects a result based on matching a discriminant value against cases.
 *
 * Syntax: switch(discriminant) { case value1: result1, case value2: result2, default: defaultResult }
 *
 * @author johsol
 */
@Beta
public final class SwitchNode extends CompositeNode {

    private final ExpressionNode discriminant;
    private final List<ExpressionNode> caseValues;
    private final List<ExpressionNode> caseResults;
    private final ExpressionNode defaultResult;

    /**
     * Creates a new switch expression node.
     *
     * @param discriminant the expression to evaluate and match against case values
     * @param caseValues the list of values to match against (one per case)
     * @param caseResults the list of results to return for each matching case
     * @param defaultResult the result to return if no case matches
     * @throws IllegalArgumentException if the number of case values doesn't match the number of case results,
     *                                  or if there are no cases
     * @throws NullPointerException if any argument is null or if caseValues/caseResults contain null elements
     */
    public SwitchNode(ExpressionNode discriminant,
                      List<ExpressionNode> caseValues,
                      List<ExpressionNode> caseResults,
                      ExpressionNode defaultResult) {
        this.discriminant = Objects.requireNonNull(discriminant, "discriminant cannot be null");
        Objects.requireNonNull(caseValues, "caseValues cannot be null");
        Objects.requireNonNull(caseResults, "caseResults cannot be null");
        this.defaultResult = Objects.requireNonNull(defaultResult, "defaultResult cannot be null");

        if (caseValues.size() != caseResults.size()) {
            throw new IllegalArgumentException("Number of case values (" + caseValues.size() +
                                               ") must match number of case results (" + caseResults.size() + ")");
        }

        if (caseValues.isEmpty()) {
            throw new IllegalArgumentException("Switch must have at least one case");
        }

        this.caseValues = List.copyOf(caseValues);
        this.caseResults = List.copyOf(caseResults);
    }

    public ExpressionNode getDiscriminant() { return discriminant; }

    public List<ExpressionNode> getCaseValues() { return caseValues; }

    public List<ExpressionNode> getCaseResults() { return caseResults; }

    public ExpressionNode getDefaultResult() { return defaultResult; }

    @Override
    public List<ExpressionNode> children() {
        List<ExpressionNode> children = new ArrayList<>();
        children.add(discriminant);
        children.addAll(caseValues);
        children.addAll(caseResults);
        children.add(defaultResult);
        return children;
    }

    @Override
    public StringBuilder toString(StringBuilder string, SerializationContext context, Deque<String> path, CompositeNode parent) {
        string.append("switch(");
        discriminant.toString(string, context, path, this);
        string.append(") { ");

        for (int i = 0; i < caseValues.size(); i++) {
            string.append("case ");
            caseValues.get(i).toString(string, context, path, this);
            string.append(": ");
            caseResults.get(i).toString(string, context, path, this);
            string.append(", ");
        }

        string.append("default: ");
        defaultResult.toString(string, context, path, this);
        string.append(" }");

        return string;
    }

    @Override
    public TensorType type(TypeContext<Reference> context) {
        validateDiscriminantCaseCompatibility(context);

        // Check that all case results
        TensorType resultType = caseResults.get(0).type(context);
        for (int i = 1; i < caseResults.size(); i++) {
            TensorType caseType = caseResults.get(i).type(context);
            final TensorType previousResultType = resultType;
            final int caseIndex = i;
            final TensorType currentCaseType = caseType;
            resultType = resultType.dimensionwiseGeneralizationWith(caseType).orElseThrow(() ->
                new IllegalArgumentException("A switch expression must produce compatible types in all cases, " +
                                             "but case 0 has type " + previousResultType + " while case " + caseIndex +
                                             " has type " + currentCaseType +
                                             "\ncase 0: " + caseResults.get(0) +
                                             "\ncase " + caseIndex + ": " + caseResults.get(caseIndex))
            );
        }

        // Check default is compatible with case results
        TensorType defaultType = defaultResult.type(context);
        final TensorType finalResultType = resultType;
        final TensorType finalDefaultType = defaultType;
        return resultType.dimensionwiseGeneralizationWith(defaultType).orElseThrow(() ->
            new IllegalArgumentException("A switch expression must produce compatible types in all cases, " +
                                         "but the case results have type " + finalResultType + " while the " +
                                         "default has type " + finalDefaultType +
                                         "\ncase results: " + caseResults.get(0) +
                                         "\ndefault: " + defaultResult)
        );
    }

    /**
     * Validates that the discriminant type is compatible with each case value type.
     */
    private void validateDiscriminantCaseCompatibility(TypeContext<Reference> context) {
        TensorType discriminantType = discriminant.type(context);

        for (int i = 0; i < caseValues.size(); i++) {
            TensorType caseValueType = caseValues.get(i).type(context);
            int caseIndex = i;
            discriminantType.dimensionwiseGeneralizationWith(caseValueType).orElseThrow(() ->
                new IllegalArgumentException("A switch expression requires the discriminant and case values to have compatible types, " +
                                             "but the discriminant has type " + discriminantType +
                                             " while case " + caseIndex + " has type " + caseValueType +
                                             "\ndiscriminant: " + discriminant +
                                             "\ncase " + caseIndex + " value: " + caseValues.get(caseIndex))
            );
        }
    }

    @Override
    public Value evaluate(Context context) {
        Value discriminantValue = discriminant.evaluate(context);

        for (int i = 0; i < caseValues.size(); i++) {
            Value caseValue = caseValues.get(i).evaluate(context);
            if (discriminantValue.equals(caseValue)) {
                return caseResults.get(i).evaluate(context);
            }
        }

        return defaultResult.evaluate(context);
    }

    @Override
    public SwitchNode setChildren(List<ExpressionNode> children) {
        int numCases = validateChildrenStructure(children);

        ExpressionNode discriminant = children.get(0);
        List<ExpressionNode> caseValues = children.subList(1, 1 + numCases);
        List<ExpressionNode> caseResults = children.subList(1 + numCases, 1 + numCases + numCases);
        ExpressionNode defaultResult = children.get(children.size() - 1);

        return new SwitchNode(discriminant, caseValues, caseResults, defaultResult);
    }

    /**
     * Validates that the children list has the correct structure for a switch node.
     * Expected structure: [discriminant, caseValue1, ..., caseValueN, caseResult1, ..., caseResultN, defaultResult]
     *
     * Returns number of cases.
     */
    private static int validateChildrenStructure(List<ExpressionNode> children) {
        if (children.size() < 4) {
            throw new IllegalArgumentException("Switch must have at least 4 children " +
                                               "(discriminant, 1 case value, 1 case result, default), but got " +
                                               children.size());
        }

        // After removing discriminant (first) and default (last), remaining must be even
        int remainingChildren = children.size() - 2;
        if (remainingChildren % 2 != 0) {
            throw new IllegalArgumentException("Invalid number of children: " + children.size() + ". " +
                                               "Expected structure: 1 discriminant + N case values + N case results + 1 default");
        }

        return remainingChildren / 2;
    }

    @Override
    public int hashCode() {
        return Objects.hash("switch", discriminant, caseValues, caseResults, defaultResult);
    }

}
