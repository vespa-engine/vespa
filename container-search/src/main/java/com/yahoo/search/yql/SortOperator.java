// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

/**
 * Represents a sort argument. ORDER BY foo; → (ASC foo)
 */
enum SortOperator implements Operator {

    ASC(ExpressionOperator.class),
    DESC(ExpressionOperator.class);

    private final ArgumentsTypeChecker checker;

    SortOperator(Object... types) {
        checker = TypeCheckers.make(this, types);
    }


    @Override
    public void checkArguments(Object... args) {
        checker.check(args);
    }

}
