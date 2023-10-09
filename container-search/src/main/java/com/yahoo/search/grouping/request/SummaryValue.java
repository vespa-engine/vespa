// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
        this(null, null, null);
    }

    /**
     * Constructs a new instance of this class.
     *
     * @param summaryName The name of the summary class to assign to this.
     */
    public SummaryValue(String summaryName) {
        this(null, null, summaryName);
    }

    private SummaryValue(String label, Integer level, String summaryName) {
        super("summary(" + (summaryName == null ? "" : summaryName)  + ")", label, level);
        name = summaryName;
    }

    @Override
    public SummaryValue copy() {
        return new SummaryValue(getLabel(), getLevelOrNull(), getSummaryName());
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
