// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import java.util.List;

import com.yahoo.search.result.Hit;
import com.yahoo.searchlib.aggregation.Grouping;

public class GroupingListHit extends Hit {

    /** for unit tests only, may give problems if grouping contains docsums */
    public GroupingListHit(List<Grouping> groupingList) {
        this(groupingList, null);
    }

    public GroupingListHit(List<Grouping> groupingList, DocsumDefinitionSet defs) {
        super("meta:grouping", 0);
        this.groupingList = groupingList;
        this.defs = defs;
    }

    public boolean isMeta() { return true; }

    public List<Grouping> getGroupingList() { return groupingList; }
    public DocsumDefinitionSet getDocsumDefinitionSet() { return defs; }

    private final List<Grouping> groupingList;
    private final DocsumDefinitionSet defs;

}
