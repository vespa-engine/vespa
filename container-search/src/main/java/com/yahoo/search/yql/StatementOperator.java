// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import com.google.inject.TypeLiteral;

import java.util.List;

/**
 * Represents program statements.
 */
enum StatementOperator implements Operator {

    PROGRAM(new TypeLiteral<List<OperatorNode<StatementOperator>>>() {
    }),
    ARGUMENT(String.class, TypeOperator.class, ExpressionOperator.class),
    DEFINE_VIEW(String.class, SequenceOperator.class),
    EXECUTE(SequenceOperator.class, String.class),
    OUTPUT(String.class),
    COUNT(String.class);

    private final ArgumentsTypeChecker checker;

    StatementOperator(Object... types) {
        checker = TypeCheckers.make(this, types);
    }


    @Override
    public void checkArguments(Object... args) {
        checker.check(args);
    }

}
