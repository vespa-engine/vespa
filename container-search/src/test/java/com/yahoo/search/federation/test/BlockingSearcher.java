// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.test;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;

/**
 * @author Tony Vaagenes
 */
public class BlockingSearcher extends Searcher {
    @Override
    public synchronized Result search(Query query, Execution execution) {
        try {
            while (true)
                wait();
        } catch (InterruptedException e) {
        }
        return execution.search(query);
    }
}
