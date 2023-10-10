// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container.searchers;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.searchchain.Execution;

/**
 * @author Christian Andersen
 */
public class MockSearcher extends Searcher {

    @Override
    public Result search(Query query, Execution execution) {
        HitGroup hits = new HitGroup();
        hits.add(new Hit("foo", query));
        return new Result(query, hits);
    }

}
