// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.searcher;

import com.yahoo.search.result.Hit;
import com.yahoo.search.result.Relevance;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;

/**
 * Save the query in the incoming state to a meta hit in the result.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */

public class QuerySnapshotSearcher extends Searcher {

    public Result search(Query query, Execution execution) {
        Query q = query.clone();
        Result r = execution.search(query);
        Hit h = new Hit("meta:querysnapshot", new Relevance(
                Double.POSITIVE_INFINITY));
        h.setMeta(true);
        h.setField("query", q);
        r.hits().add(h);
        return r;
    }
}
