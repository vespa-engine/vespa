// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.streamingvisitors;

import com.yahoo.document.select.parser.ParseException;
import com.yahoo.prelude.fastsearch.TimeoutException;
import com.yahoo.searchlib.aggregation.Grouping;
import com.yahoo.vdslib.DocumentSummary;
import com.yahoo.vdslib.SearchResult;
import com.yahoo.vdslib.VisitorStatistics;

import java.util.List;
import java.util.Map;

/**
 * Visitor for performing searches and accessing results.
 *
 * @author <a href="mailto:ulf@yahoo-inc.com">Ulf Carlin</a>
 */
interface Visitor {

    void doSearch() throws InterruptedException, ParseException, TimeoutException;

    VisitorStatistics getStatistics();

    List<SearchResult.Hit> getHits();

    Map<String, DocumentSummary.Summary> getSummaryMap();

    int getTotalHitCount();

    List<Grouping> getGroupings();
}
