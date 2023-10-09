// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import java.util.Set;

/**
 * Check that an argument is an OperatorNode of a particular operator set.
 */
class NodeTypeChecker extends OperatorTypeChecker {

    private final Class<? extends Operator> operatorType;
    private final Set<? extends Operator> operators;

    public NodeTypeChecker(Operator parent, int idx, Class<? extends Operator> operatorType, Set<? extends Operator> operators) {
        super(parent, idx);
        this.operatorType = operatorType;
        this.operators = operators;
    }

    @Override
    public void check(Object argument) {
        Preconditions.checkNotNull(argument, "Argument %s of %s must not be null", idx, parent);
        Preconditions.checkArgument(argument instanceof OperatorNode, "Argument %s of %s must be an OperatorNode<%s> (is %s).", idx, parent, operatorType.getName(), argument.getClass());
        OperatorNode<?> node = (OperatorNode<?>) argument;
        Operator op = node.getOperator();
        Preconditions.checkArgument(operatorType.isInstance(op), "Argument %s of %s must be an OperatorNode<%s> (is: %s).", idx, parent, operatorType.getName(), op.getClass());
        if (!operators.isEmpty()) {
            Preconditions.checkArgument(operators.contains(op), "Argument %s of %s must be %s (is %s).", idx, parent, Joiner.on("|").join(operators), op);
        }
    }

}
