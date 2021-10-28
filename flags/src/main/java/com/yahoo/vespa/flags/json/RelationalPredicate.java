// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.json;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author hakonhall
 */
public class RelationalPredicate {
    private final String originalPredicateString;
    private final RelationalOperator operator;
    private final String rightOperand;

    /** @param predicateString is e.g. "&gt; SUFFIX" or "&lt;=SUFFIX". The first part is {@link RelationalOperator}. */
    public static RelationalPredicate fromWire(String predicateString) {
        // Make sure we try to match e.g. "<=" before "<" as the prefix of predicateString.
        List<RelationalOperator> operatorsByDecendingLength = Stream.of(RelationalOperator.values())
                .sorted(Comparator.comparing(operator -> - operator.toText().length()))
                .collect(Collectors.toList());

        for (var operator : operatorsByDecendingLength) {
            if (predicateString.startsWith(operator.toText())) {
                String suffix = predicateString.substring(operator.toText().length()).trim();
                return new RelationalPredicate(predicateString, operator, suffix);
            }
        }

        throw new IllegalArgumentException("Predicate string '" + predicateString + "' does not start with a relation operator");
    }

    private RelationalPredicate(String originalPredicateString, RelationalOperator operator, String rightOperand) {
        this.originalPredicateString = originalPredicateString;
        this.operator = operator;
        this.rightOperand = rightOperand;
    }

    public RelationalOperator operator() { return operator; }
    public String rightOperand() { return rightOperand; }
    public String toWire() { return originalPredicateString; }
}
