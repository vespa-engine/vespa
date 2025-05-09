// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.documentmodel;

/**
 * A class selecting which summary elements of a multi-value field to render.
 */
public final class SummaryElementsSelector {
    public enum Select {
        ALL,
        BY_MATCH,
        BY_SUMMARY_FEATURE
    }

    private final Select select;
    private final String summaryFeature;
    private static final SummaryElementsSelector all = new SummaryElementsSelector(Select.ALL, "");
    private static final SummaryElementsSelector byMatch = new SummaryElementsSelector(Select.BY_MATCH, "");

    private SummaryElementsSelector(Select select, String summaryFeature) {
        this.select = select;
        this.summaryFeature = summaryFeature;
    }

    public Select getSelect() { return select; }
    public String getSummaryFeature() { return summaryFeature; }

    public static SummaryElementsSelector selectAll() {
        return all;
    }

    public static SummaryElementsSelector selectByMatch() {
        return byMatch;
    }
}
