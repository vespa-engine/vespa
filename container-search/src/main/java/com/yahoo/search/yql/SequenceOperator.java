// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import com.google.common.base.Predicate;
import com.google.inject.TypeLiteral;

import java.util.List;

/**
 * Logical sequence operators represent a logical description of a "source" (query against data stores + pipes), representing
 * a source_expression in the grammar.
 */
enum SequenceOperator implements Operator {

    SCAN(TypeCheckers.LIST_OF_STRING, TypeCheckers.EXPRS),    // scan a named data source (with optional arguments)
    /**
     * INSERT(target-sequence, input-records)
     */
    INSERT(SequenceOperator.class, SequenceOperator.class),
    UPDATE(SequenceOperator.class, ExpressionOperator.MAP, ExpressionOperator.class),
    UPDATE_ALL(SequenceOperator.class, ExpressionOperator.MAP),
    DELETE(SequenceOperator.class, ExpressionOperator.class),
    DELETE_ALL(SequenceOperator.class),
    EMPTY(),    // emits a single, empty row
    // evaluate the given expression and use the result as a sequence
    EVALUATE(ExpressionOperator.class),
    NEXT(String.class),

    PROJECT(SequenceOperator.class, new TypeLiteral<List<OperatorNode<ProjectOperator>>>() {
    }), // transform a sequence into a new schema
    FILTER(SequenceOperator.class, ExpressionOperator.class),  // filter a sequence by an expression
    SORT(SequenceOperator.class, new TypeLiteral<List<OperatorNode<SortOperator>>>() {
    }),    // sort a sequence
    PIPE(SequenceOperator.class, TypeCheckers.LIST_OF_STRING, TypeCheckers.EXPRS),    // pipe from one source through a named transformation
    LIMIT(SequenceOperator.class, ExpressionOperator.class),
    OFFSET(SequenceOperator.class, ExpressionOperator.class),
    SLICE(SequenceOperator.class, ExpressionOperator.class, ExpressionOperator.class),
    MERGE(TypeCheckers.SEQUENCES),
    JOIN(SequenceOperator.class, SequenceOperator.class, ExpressionOperator.class),     // combine two (or more, in the case of MERGE) sequences to produce a new sequence
    LEFT_JOIN(SequenceOperator.class, SequenceOperator.class, ExpressionOperator.class),

    FALLBACK(SequenceOperator.class, SequenceOperator.class),

    TIMEOUT(SequenceOperator.class, ExpressionOperator.class),
    PAGE(SequenceOperator.class, ExpressionOperator.class),
    ALL(),
    MULTISOURCE(TypeCheckers.LIST_OF_LIST_OF_STRING);

    private final ArgumentsTypeChecker checker;

    public static Predicate<OperatorNode<? extends Operator>> IS = new Predicate<OperatorNode<? extends Operator>>() {
        @Override
        public boolean apply(OperatorNode<? extends Operator> input) {
            return input.getOperator() instanceof SequenceOperator;
        }
    };

    private SequenceOperator(Object... types) {
        checker = TypeCheckers.make(this, types);
    }


    @Override
    public void checkArguments(Object... args) {
        checker.check(args);
    }

}
