// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import java.util.List;

import com.yahoo.fs4.QueryPacketData;
import com.yahoo.search.result.Hit;
import com.yahoo.searchlib.aggregation.Grouping;

public class GroupingListHit extends Hit {

    private static final long serialVersionUID = -6645125887873082234L;

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
    private QueryPacketData queryPacketData;

    public void setQueryPacketData(QueryPacketData queryPacketData) {
        this.queryPacketData = queryPacketData;
    }

    /** Returns encoded query data from the query used to create this, or null if none present */
    public QueryPacketData getQueryPacketData() {
        return queryPacketData;
    }

}
