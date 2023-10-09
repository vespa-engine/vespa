// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.searcher;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;

/**
 * This searcher fills the results in the first phase. May be put into
 * a search chain to ensure full results are present at an earlier
 * time than they would normally be.
 *
 * @author havardpe
 */
public class FillSearcher extends Searcher {

    public FillSearcher() { }

    @Override
    public Result search(Query query, Execution execution) {
        Result result;
        result = execution.search(query);
        execution.fill(result);
        return result;
    }

}
