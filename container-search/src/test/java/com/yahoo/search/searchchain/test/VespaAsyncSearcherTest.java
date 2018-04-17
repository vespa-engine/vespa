// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchchain.test;

import com.yahoo.component.chain.Chain;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.AsyncExecution;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.FutureResult;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Externally provided test for async execution of search chains.
 *
 * @author Peter Thomas
 */
public class VespaAsyncSearcherTest {

    private static class FirstSearcher extends Searcher {

        @Override
        public Result search(Query query, Execution exctn) {
            int count = 10;
            List<FutureResult> futures = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                Query subQuery = new Query();
                FutureResult future = new AsyncExecution(exctn)
                        .search(subQuery);
                futures.add(future);
            }
            AsyncExecution.waitForAll(futures, 10 * 60 * 1000);
            return new Result(query);
        }

    }

    private static class SecondSearcher extends Searcher {

        @Override
        public Result search(Query query, Execution exctn) {
            return new Result(query);
        }

    }

    @Test
    public void testAsyncExecution() {
        Chain<Searcher> chain = new Chain<>(new FirstSearcher(), new SecondSearcher());
        Execution execution = new Execution(chain, Execution.Context.createContextStub(null));
        Query query = new Query();
        // fails with exception on old versions
        execution.search(query);
    }

}
