// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.BoolFieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.document.datatypes.PredicateFieldValue;
import com.yahoo.document.predicate.FeatureRange;
import com.yahoo.document.predicate.Predicate;
import com.yahoo.document.predicate.PredicateOperator;
import com.yahoo.search.predicate.optimization.AndOrSimplifier;
import com.yahoo.search.predicate.optimization.BooleanSimplifier;
import com.yahoo.search.predicate.optimization.ComplexNodeTransformer;
import com.yahoo.search.predicate.optimization.NotNodeReorderer;
import com.yahoo.search.predicate.optimization.PredicateOptions;
import com.yahoo.search.predicate.optimization.PredicateProcessor;

/**
 * @author Simon Thoresen Hult
 */
public final class OptimizePredicateExpression extends Expression {

    private final PredicateProcessor optimizer;

    public OptimizePredicateExpression() {
        this(new PredicateOptimizer());
    }

    OptimizePredicateExpression(PredicateProcessor optimizer) {
        this.optimizer = optimizer;
    }

    // Not technically true, but the semantics are not changed. */
    @Override
    public boolean isMutating() { return false; }

    @Override
    public DataType setInputType(DataType inputType, TypeContext context) {
        var input = super.setInputType(inputType, DataType.PREDICATE, context);
        checkVariable(context, "arity", DataType.INT, true);
        checkVariable(context, "lower_bound", DataType.LONG, false);
        checkVariable(context, "upper_bound", DataType.LONG, false);
        checkVariable(context, "reject_predicate_feed_outside_bounds", DataType.BOOL, false);
        return input;
    }

    @Override
    public DataType setOutputType(DataType outputType, TypeContext context) {
        return super.setOutputType(DataType.PREDICATE, outputType, null, context);
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        PredicateFieldValue predicate = ((PredicateFieldValue) context.getCurrentValue()).clone();
        IntegerFieldValue arity = (IntegerFieldValue) context.getVariable("arity");
        LongFieldValue lower_bound = (LongFieldValue) context.getVariable("lower_bound");
        LongFieldValue upper_bound = (LongFieldValue) context.getVariable("upper_bound");
        Long lower = lower_bound != null? lower_bound.getLong() : null;
        Long upper = upper_bound != null? upper_bound.getLong() : null;
        PredicateOptions options = new PredicateOptions(arity.getInteger(), lower, upper);
        if (context.getVariable("reject_predicate_feed_outside_bounds") instanceof BoolFieldValue reject && reject.getBoolean())
            validateBounds(predicate.getPredicate(), options);
        predicate.setPredicate(optimizer.process(predicate.getPredicate(), options));
        context.setCurrentValue(predicate);
    }

    private static void validateBounds(Predicate predicate, PredicateOptions options) {
        if (predicate instanceof PredicateOperator operator) {
            for (Predicate operand : operator.getOperands()) {
                validateBounds(operand, options);
            }
        } else if (predicate instanceof FeatureRange range) {
            Long from = range.getFromInclusive();
            Long to = range.getToInclusive();
            long lower = options.getLowerBound();
            long upper = options.getUpperBound();
            if ((from != null && (from < lower || from > upper)) ||
                (to != null && (to < lower || to > upper))) {
                throw new InvalidInputException("Predicate attribute '" + range.getKey() + "' has range [" +
                                                (from == null ? "" : from) + ".." + (to == null ? "" : to) +
                                                "] outside configured bounds [" + lower + ".." + upper + "].");
            }
        }
    }

    private void checkVariable(TypeContext context, String var, DataType type, boolean required) {
        DataType input = context.getVariableType(var);
        if (input == null) {
            if (required)
                throw new VerificationException(this, "Variable '" + var + "' must be set");
        } else if (input != type) {
            throw new VerificationException(this, "Variable '" + var + "' must have type " + type.getName());
        }
    }

    @Override
    public String toString() { return "optimize_predicate"; }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof OptimizePredicateExpression;
    }

    private static class PredicateOptimizer implements PredicateProcessor {

        private final ComplexNodeTransformer complexNodeTransformer = new ComplexNodeTransformer();
        private final BooleanSimplifier booleanSimplifier = new BooleanSimplifier();
        private final AndOrSimplifier andOrSimplifier = new AndOrSimplifier();
        private final NotNodeReorderer notNodeReorderer = new NotNodeReorderer();

        @Override
        public Predicate process(Predicate predicate, PredicateOptions options) {
            Predicate processedPredicate = complexNodeTransformer.process(predicate, options);
            processedPredicate = booleanSimplifier.process(processedPredicate, options);
            processedPredicate = andOrSimplifier.process(processedPredicate, options);
            return notNodeReorderer.process(processedPredicate, options);
        }
    }

}
