// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

import com.yahoo.api.annotations.Beta;

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
    private final GroupingExpression indexArgument;

    /**
     * Constructs a new instance of this class.
     *
     * @param attributeName the attribute name to assign to this.
     */
    public ArrayAtLookup(String attributeName, GroupingExpression indexArg) {
        this(null, null, attributeName, indexArg);
    }

    private ArrayAtLookup(String label, Integer level, String attributeName, GroupingExpression indexArgument) {
        super("array.at(" + attributeName + ", " + indexArgument + ")", label, level);
        this.attributeName = attributeName;
        this.indexArgument = indexArgument;
    }

    @Override
    public ArrayAtLookup copy() {
        return new ArrayAtLookup(getLabel(), getLevelOrNull(), getAttributeName(), getIndexArgument().copy());
    }

    /** Returns the name of the attribute to retrieve from the input hit */
    public String getAttributeName() {
        return attributeName;
    }

    /** Return the expression to evaluate before indexing */
    public GroupingExpression getIndexArgument() {
        return indexArgument;
    }

}
