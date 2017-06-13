// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchchain.config.test.twosearchers;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;

/**
 * @author bratseth
 */
public class MultiSearcher2 extends Searcher {

    public Result search(Query query, Execution execution) {
        return execution.search(query);
    }

}
