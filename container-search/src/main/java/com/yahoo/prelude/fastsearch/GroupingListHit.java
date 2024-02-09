// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import java.util.List;

import com.yahoo.search.Query;
import com.yahoo.search.result.Hit;
import com.yahoo.search.schema.Schema;
import com.yahoo.searchlib.aggregation.Grouping;

public class GroupingListHit extends Hit {

    /** for unit tests only, may give problems if grouping contains docsums */
    public GroupingListHit(List<Grouping> groupingList) {
        this(groupingList, null, null);
    }

    public GroupingListHit(List<Grouping> groupingList, DocumentDatabase documentDatabase, Query query) {
        super("meta:grouping", 0, query);
        this.groupingList = groupingList;
        this.documentDatabase = documentDatabase;
    }

    public boolean isMeta() { return true; }

    public List<Grouping> getGroupingList() { return groupingList; }
    public DocsumDefinitionSet getDocsumDefinitionSet() { return documentDatabase.getDocsumDefinitionSet(); }
    public Schema getSchema() { return documentDatabase.schema(); }
    public DocumentDatabase getDocumentDatBase() { return documentDatabase; }

    private final List<Grouping> groupingList;
    private final DocumentDatabase documentDatabase;

}
