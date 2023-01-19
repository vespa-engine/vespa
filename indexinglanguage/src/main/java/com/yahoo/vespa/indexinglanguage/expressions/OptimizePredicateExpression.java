// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.document.datatypes.PredicateFieldValue;
import com.yahoo.document.predicate.Predicate;
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
        super(DataType.PREDICATE);
        this.optimizer = optimizer;
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        PredicateFieldValue predicate = ((PredicateFieldValue) context.getValue()).clone();
        IntegerFieldValue arity = (IntegerFieldValue) context.getVariable("arity");
        LongFieldValue lower_bound = (LongFieldValue) context.getVariable("lower_bound");
        LongFieldValue upper_bound = (LongFieldValue) context.getVariable("upper_bound");
        Long lower = lower_bound != null? lower_bound.getLong() : null;
        Long upper = upper_bound != null? upper_bound.getLong() : null;
        PredicateOptions options = new PredicateOptions(arity.getInteger(), lower, upper);
        predicate.setPredicate(optimizer.process(predicate.getPredicate(), options));
        context.setValue(predicate);
    }

    @Override
    protected void doVerify(VerificationContext context) {
        checkVariable(context, "arity", DataType.INT, true);
        checkVariable(context, "lower_bound", DataType.LONG, false);
        checkVariable(context, "upper_bound", DataType.LONG, false);
        context.setValueType(DataType.PREDICATE);
    }

    private void checkVariable(VerificationContext ctx, String var, DataType type, boolean required) {
        DataType input = ctx.getVariable(var);
        if (input == null) {
            if (required) {
                throw new VerificationException(this, "Variable '" + var + "' must be set.");
            }
        } else if (input != type) {
            throw new VerificationException(this, "Variable '" + var + "' must have type " + type.getName() + ".");
        }
    }

    @Override
    public DataType createdOutputType() {
        return null;
    }

    @Override
    public String toString() {
        return "optimize_predicate";
    }

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
