// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.json;

import java.util.Objects;
import java.util.function.Function;

/**
 * @author hakonhall
 */
public enum RelationalOperator {
    EQUAL        ("==", compareToValue -> compareToValue == 0),
    NOT_EQUAL    ("!=", compareToValue -> compareToValue != 0),
    LESS_EQUAL   ("<=", compareToValue -> compareToValue <= 0),
    LESS         ("<" , compareToValue -> compareToValue <  0),
    GREATER_EQUAL(">=", compareToValue -> compareToValue >= 0),
    GREATER      (">" , compareToValue -> compareToValue >  0);

    private String text;
    private final Function<Integer, Boolean> compareToValuePredicate;

    RelationalOperator(String text, Function<Integer, Boolean> compareToValuePredicate) {
        this.text = text;
        this.compareToValuePredicate = compareToValuePredicate;
    }

    public String toText() { return text; }

    /** Returns true if 'left op right' is true, with 'op' being the operator represented by this. */
    public <T extends Comparable<T>> boolean evaluate(T left, T right) {
        Objects.requireNonNull(left);
        Objects.requireNonNull(right);
        return evaluate(left.compareTo(right));
    }

    public boolean evaluate(int compareToValue) {
        return compareToValuePredicate.apply(compareToValue);
    }
}

