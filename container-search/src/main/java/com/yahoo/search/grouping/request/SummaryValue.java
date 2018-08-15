// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request;

/**
 * This class represents a document summary in a {@link GroupingExpression}. It evaluates to the summary of the input
 * {@link com.yahoo.search.result.Hit} that corresponds to the named summary class.
 *
 * @author Simon Thoresen Hult
 */
public class SummaryValue extends DocumentValue {

    private final String name;

    /**
     * Constructs a new instance of this class, using the default summary class.
     */
    public SummaryValue() {
        super("summary()");
        name = null;
    }

    /**
     * Constructs a new instance of this class.
     *
     * @param summaryName The name of the summary class to assign to this.
     */
    public SummaryValue(String summaryName) {
        super("summary(" + summaryName + ")");
        name = summaryName;
    }

    /**
     * Returns the name of the summary class used to retrieve the hit from the search node.
     *
     * @return The summary name.
     */
    public String getSummaryName() {
        return name;
    }
}
