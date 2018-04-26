// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.treenet.rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Represents a set membership test on the form <code>feature IN (integer1, integer2 ...)</code>
 *
 * @author bratseth
 * @since   5.1.21
 */
public class SetMembershipCondition extends Condition {

    private final List<Object> setValues;

    /**
     * Constructs a new instance of this class.
     *
     * @param testValue the name of the feature to test
     * @param setValues the set of values to compare to
     * @param trueLabel the label to jump to if the value is in the set
     * @param falseLabel the label to jumt to if the value is not in the set
     */
    public SetMembershipCondition(String testValue, List<Object> setValues, String trueLabel, String falseLabel) {
        super(testValue, trueLabel, falseLabel);
        this.setValues = Collections.unmodifiableList(new ArrayList<>(setValues));
    }

    /** Returns the unmodifiable set of values to check */
    public List<Object> getSetValues() { return setValues; }

    @Override
    protected String conditionToRankingExpression() {
        StringBuilder b = new StringBuilder("in [");
        for (Iterator<Object> i = setValues.iterator(); i.hasNext(); ) {
            Object value = i.next();
            if (value instanceof String)
                b.append("\"").append(value).append("\"");
            else if (value instanceof Integer)
                b.append(value);
            else
                throw new RuntimeException("Excepted a string or integer in a set membership test, not a " +
                                           value.getClass() + ": " + value);

            if (i.hasNext())
                b.append(",");
        }
        b.append("]");
        return b.toString();
    }

}
