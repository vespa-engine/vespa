// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;

/**
 * @author bratseth
 */
public class MockResultSearcher extends Searcher {

    @Override
    public Result search(Query query, Execution execution) {
        Result result = new Result(query);
        result.hits().add(new Hit("hasQuery", query));
        return result;
    }

}
