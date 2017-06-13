// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;

/**
 * This class represents an uca-function in a {@link GroupingExpression}.
 *
 * @author <a href="mailto:lulf@yahoo-inc.com">Ulf Lilleengen</a>
 */
public class UcaFunction extends FunctionNode {

    private final String locale;
    private final String strength;


    /**
     * Constructs a new instance of this class.
     *
     * @param exp     The expression to evaluate.
     * @param locale  The locale to used for sorting.
     */
    public UcaFunction(GroupingExpression exp, String locale) {
        super("uca", Arrays.asList(exp, new StringValue(locale)));
        this.locale = locale;
        this.strength = "TERTIARY";
    }

    /**
     * Constructs a new instance of this class.
     *
     * @param exp     The expression to evaluate.
     * @param locale  The locale to used for sorting.
     * @param strength The strength level to use.
     */
    public UcaFunction(GroupingExpression exp, String locale, String strength) {
        super("uca", Arrays.asList(exp, new StringValue(locale), new StringValue(strength)));
        if (!validStrength(strength)) {
            throw new IllegalArgumentException("Not a valid UCA strength: " + strength);
        }
        this.locale = locale;
        this.strength = strength;
    }

    private boolean validStrength(String strength) {
        return (strength.equals("PRIMARY") ||
                strength.equals("SECONDARY") ||
                strength.equals("TERTIARY") ||
                strength.equals("QUATERNARY") ||
                strength.equals("IDENTICAL"));
    }

    public String getLocale() {
        return locale;
    }

    public String getStrength() {
        return strength;
    }
}



