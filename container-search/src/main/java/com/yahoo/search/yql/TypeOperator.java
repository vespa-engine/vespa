// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import com.google.common.base.Predicate;

enum TypeOperator implements Operator {

    BYTE,
    INT16,
    INT32,
    INT64,
    STRING,
    DOUBLE,
    TIMESTAMP,
    BOOLEAN,
    ARRAY(TypeOperator.class),
    MAP(TypeOperator.class);

    private final ArgumentsTypeChecker checker;

    public static Predicate<OperatorNode<? extends Operator>> IS = new Predicate<OperatorNode<? extends Operator>>() {
        @Override
        public boolean apply(OperatorNode<? extends Operator> input) {
            return input.getOperator() instanceof TypeOperator;
        }
    };

    TypeOperator(Object... types) {
        checker = TypeCheckers.make(this, types);
    }

    @Override
    public void checkArguments(Object... args) {
        checker.check(args);
    }

}
