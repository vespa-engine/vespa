// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import com.google.common.annotations.Beta;

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
    private final GroupingExpression arg2;

    /**
     * Constructs a new instance of this class.
     *
     * @param attributeName The attribute name the lookup should happen in
     * @param lookupArg Expression giving a floating-point value for the lookup argument
     */
    public InterpolatedLookup(String attributeName, GroupingExpression lookupArg) {
        super("interpolatedlookup(" + attributeName + ", " + lookupArg + ")");
        this.attributeName = attributeName;
	this.arg2 = lookupArg;
    }

    /**
     * Get the name of the attribute to be retrieved from the input hit.
     * @return The attribute name.
     */
    public String getAttributeName() {
        return attributeName;
    }

    /**
     * Get the expression that will be evaluated before lookup.
     * @return grouping expression argument
     */
    public GroupingExpression getLookupArgument() {
	return arg2;
    }

}
