// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.test.integration;

import com.yahoo.search.result.Hit;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;

public class SecondSearcher extends Searcher {
    public Result search(com.yahoo.search.Query query, Execution execution) {
        Result result = execution.search(query);
        result.hits().add(new Hit("searcher:2",996));
        return result;
    }
}
