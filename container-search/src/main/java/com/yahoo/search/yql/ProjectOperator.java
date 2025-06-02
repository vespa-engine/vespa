// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import com.google.common.base.Predicate;

/**
 * Represents a projection command which affects the output record.
 */
enum ProjectOperator implements Operator {

    FIELD(ExpressionOperator.class, String.class),  // FIELD expr name
    RECORD(ExpressionOperator.class, String.class), // RECORD expr name
    MERGE_RECORD(String.class);                     // MERGE_RECORD name (alias of record to merge)

    private final ArgumentsTypeChecker checker;

    public static Predicate<OperatorNode<? extends Operator>> IS = new Predicate<OperatorNode<? extends Operator>>() {
        @Override
        public boolean apply(OperatorNode<? extends Operator> input) {
            return input.getOperator() instanceof ProjectOperator;
        }
    };

    private ProjectOperator(Object... types) {
        checker = TypeCheckers.make(this, types);
    }


    @Override
    public void checkArguments(Object... args) {
        checker.check(args);
    }

}
