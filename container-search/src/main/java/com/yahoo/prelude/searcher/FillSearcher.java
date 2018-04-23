// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.searcher;

import com.yahoo.component.ComponentId;
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

    private final Searcher next;

    public FillSearcher() {
        next = null;
    }

    public FillSearcher(Searcher next) {
        this.next = next;
    }

    @Override
    public Result search(Query query, Execution execution) {
        Result result;
        if (next == null) {
            result = execution.search(query);
            execution.fill(result);
        } else {
            Execution e = new Execution(next, execution.context());
            result = e.search(query);
            e.fill(result);
        }
        return result;
    }

    // TODO: Remove this method as it does nothing new
    @Override
    public void fill(Result result, String summaryClass, Execution execution) {
        if (next == null) {
            execution.fill(result, summaryClass);
        } else {
            Execution e = new Execution(next, execution.context());
            e.fill(result, summaryClass);
        }
    }

}
