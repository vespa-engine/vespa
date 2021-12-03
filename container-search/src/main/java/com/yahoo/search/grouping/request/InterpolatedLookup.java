// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import com.yahoo.api.annotations.Beta;

/**
 * This class represents a lookup in a multivalue document
 * attribute in a {@link GroupingExpression}.  It takes the
 * attribute (assumed to contain a sorted array) from the input
 * {@link com.yahoo.search.result.Hit} and finds the index that
 * the second (lookup) argument expression would have, with linear
 * interpolation when the lookup argument is between two array
 * element values.
 *
 * @author arnej27959
 */
@Beta
public class InterpolatedLookup extends DocumentValue {

    private final String attributeName;
    private final GroupingExpression lookupArgument;

    /**
     * Constructs a new instance of this class.
     *
     * @param attributeName the attribute name to assign to this.
     * @param lookupArgument Expression giving a floating-point value for the lookup argument
     */
    public InterpolatedLookup(String attributeName, GroupingExpression lookupArgument) {
        this(null, null, attributeName, lookupArgument);
    }

    private InterpolatedLookup(String label, Integer level, String attributeName, GroupingExpression lookupArgument) {
        super("interpolatedlookup(" + attributeName + ", " + lookupArgument + ")", label, level);
        this.attributeName = attributeName;
        this.lookupArgument = lookupArgument;
    }

    @Override
    public InterpolatedLookup copy() {
        return new InterpolatedLookup(getLabel(), getLevelOrNull(), getAttributeName(), getLookupArgument().copy());
    }

    /** Returns the name of the attribute to retrieve from the input hit */
    public String getAttributeName() {
        return attributeName;
    }

    /** Return the expression to evaluate before lookup */
    public GroupingExpression getLookupArgument() {
        return lookupArgument;
    }

}
