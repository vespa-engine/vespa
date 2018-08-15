// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import com.google.common.annotations.Beta;

/**
 * Represents access of array element in a document attribute in a {@link GroupingExpression}.
 *
 * The first argument should be the name of an array attribute in the
 * input {@link com.yahoo.search.result.Hit}, while the second
 * argument is evaluated as an integer and used as the index in that array.
 * If the index argument is less than 0 returns the first array element;
 * if the index is greater than or equal to size(array) returns the last array element;
 * if the array is empty returns 0 (or NaN?).
 *
 * @author arnej27959
 */
@Beta
public class ArrayAtLookup extends DocumentValue {

    private final String attributeName;
    private final GroupingExpression arg2;

    /**
     * Constructs a new instance of this class.
     *
     * @param attributeName The attribute name to assign to this.
     */
    public ArrayAtLookup(String attributeName, GroupingExpression indexArg) {
        super("array.at(" + attributeName + ", " + indexArg + ")");
        this.attributeName = attributeName;
        this.arg2 = indexArg;
    }

    /**
     * Returns the name of the attribute to retrieve from the input hit.
     *
     * @return The attribute name.
     */
    public String getAttributeName() {
        return attributeName;
    }

    /**
     * get the expression to evaluate before indexing
     * @return grouping expression argument
     */
    public GroupingExpression getIndexArgument() {
	return arg2;
    }

}
