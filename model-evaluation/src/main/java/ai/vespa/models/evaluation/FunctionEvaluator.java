// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

/**
 * An evaluator which can be used to evaluate a single function once.
 *
 * @author bratseth
 */
// This wraps all access to the context and the ranking expression to avoid incorrect usage
public class FunctionEvaluator {

    private final ExpressionFunction function;
    private final LazyArrayContext context;
    private boolean evaluated = false;

    FunctionEvaluator(ExpressionFunction function, LazyArrayContext context) {
        this.function = function;
        this.context = context;
    }

    /**
     * Binds the given variable referred in this expression to the given value.
     *
     * @param name the variable to bind
     * @param value the value this becomes bound to
     * @return this for chaining
     */
    public FunctionEvaluator bind(String name, Tensor value) {
        if (evaluated)
            throw new IllegalStateException("You cannot bind a value in a used evaluator");
        context.put(name, new TensorValue(value));
        return this;
    }

    /**
     * Binds the given variable referred in this expression to the given value.
     * This is equivalent to <code>bind(name, Tensor.Builder.of(TensorType.empty).cell(value).build())</code>
     *
     * @param name the variable to bind
     * @param value the value this becomes bound to
     * @return this for chaining
     */
    public FunctionEvaluator bind(String name, double value) {
        return bind(name, Tensor.Builder.of(TensorType.empty).cell(value).build());
    }

    public Tensor evaluate() {
        evaluated = true;
        return function.getBody().evaluate(context).asTensor();
    }

}
