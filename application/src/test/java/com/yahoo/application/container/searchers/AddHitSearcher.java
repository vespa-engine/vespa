// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container.searchers;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;

public class AddHitSearcher extends Searcher {
    @Override
    public Result search(Query query, Execution execution) {
        Result result = execution.search(query);
        result.hits().add(getDummyHit());

        return result;
    }

    private Hit getDummyHit() {
        Hit hit = new Hit("dummy");
        hit.setField("title", getId().getName());
        return hit;
    }
}
