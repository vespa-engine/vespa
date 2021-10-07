// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.context.test;

import java.util.ArrayList;
import java.util.List;

import com.yahoo.component.chain.Chain;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.AsyncExecution;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.FutureResult;

/**
 * Checks it's OK adding more traces to an instance which is being rendered.
 *
 * @author <a href="arnebef@yahoo-inc.com">Arne Bergene Fossaa</a>
 */
@SuppressWarnings("deprecation")
public class ConcurrentTraceTestCase {
   class TraceSearcher extends Searcher {

        @Override
        public Result search(Query query, Execution execution) {
            for(int i = 0;i<1000;i++) {
                query.trace("Trace", false, 1);
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
            }
            return execution.search(query);
        }
    }

    class AsyncSearcher extends Searcher {

        @Override
        public Result search(Query query, Execution execution) {
            Chain<Searcher> chain = new Chain<>(new TraceSearcher());

            Result result = new Result(query);
            List<FutureResult> futures = new ArrayList<>();
            for(int i = 0; i < 100; i++) {
                futures.add(new AsyncExecution(chain, execution).searchAndFill(query));
            }
            AsyncExecution.waitForAll(futures, 10);
            return result;
        }

    }

}
