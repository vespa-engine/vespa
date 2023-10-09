// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchchain.example;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;

/**
 * An example searcher which adds a hit
 *
 * @author bratseth
 */
public class ExampleSearcher extends Searcher {

    @Override
    public Result search(Query query,Execution execution) {
        Result result=execution.search(query);
        result.hits().add(new Hit("example",1.0,"examplesearcher"));
        return result;
    }

}
