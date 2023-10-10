// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class represents an uca-function in a {@link GroupingExpression}.
 *
 * @author Ulf Lilleengen
 * @author bratseth
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
        this(null, null, Arrays.asList(exp, new StringValue(locale)));
    }

    /**
     * Constructs a new instance of this class.
     *
     * @param exp     The expression to evaluate.
     * @param locale  The locale to used for sorting.
     * @param strength The strength level to use.
     */
    public UcaFunction(GroupingExpression exp, String locale, String strength) {
        this(null, null, Arrays.asList(exp, new StringValue(locale), new StringValue(strength)));
        if ( ! validStrength(strength))
            throw new IllegalArgumentException("Not a valid UCA strength: " + strength);
    }

    private UcaFunction(String label, Integer level, List<GroupingExpression> args) {
        super("uca", label, level, args);
        this.locale = ((StringValue)args.get(1)).getValue();
        this.strength = args.size() > 2 ? ((StringValue)args.get(2)).getValue() : "TERTIARY";
    }

    @Override
    public UcaFunction copy() {
        return new UcaFunction(getLabel(),
                               getLevelOrNull(),
                               args().stream().map(arg -> arg.copy()).toList());
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



