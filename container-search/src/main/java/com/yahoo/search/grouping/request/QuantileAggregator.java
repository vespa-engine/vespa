// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This class represents a quantile-aggregator in a {@link GroupingExpression}. It evaluates to the quantiles of
 * the contained expression evaluated to over all the inputs.
 *
 * @author johsol
 */
public class QuantileAggregator extends AggregatorNode {

    private final List<Double> quantiles;

    /**
     * Constructs a new instance of this class.
     *
     * @param quantiles  the quantiles to be aggregated.
     * @param expression the expression to aggregate on.
     */
    public QuantileAggregator(List<Number> quantiles, GroupingExpression expression) {
        this(null, null, quantiles, expression);
    }

    private QuantileAggregator(String label, Integer level, List<Number> quantiles, GroupingExpression expression) {
        super("quantiles", label, level, expression);
        this.quantiles = toValidatedQuantiles(quantiles);
    }

    /**
     * Checks that the quantiles are not null and that they are between
     * 0.0 and 1.0. Converts each quantile to double, removes copies and
     * sorts the list of quantiles.
     *
     * @param quantiles the quantiles to be validated.
     */
    private List<Double> toValidateQuantiles(List<Number> quantiles) {
        if (quantiles.isEmpty()) {
            throw new IllegalArgumentException("quantiles cannot be empty");
        }

        for (Number quantile : quantiles) {
            if (quantile == null) {
                throw new IllegalArgumentException("quantile cannot be null");
            }

            if (quantile.doubleValue() < 0.0 || quantile.doubleValue() > 1.0) {
                throw new IllegalArgumentException("quantile must be between 0.0 and 1.0");
            }
        }

        return quantiles.stream().map(Number::doubleValue).distinct().sorted().toList();
    }

    public List<Double> getQuantiles() {
        return quantiles;
    }

    @Override
    public QuantileAggregator copy() {
        return new QuantileAggregator(getLabel(), getLevelOrNull(), List.copyOf(getQuantiles()), getExpression().copy());
    }

    @Override
    public String toString() {
        String quantileStr = quantiles.stream().map(String::valueOf).collect(Collectors.joining(","));
        return "quantiles([" + quantileStr + "]," + getExpression() + ")";
    }
}
