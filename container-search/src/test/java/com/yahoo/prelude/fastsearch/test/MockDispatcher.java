package com.yahoo.prelude.fastsearch.test;

import com.yahoo.prelude.fastsearch.FS4ResourcePool;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.Dispatcher;
import com.yahoo.vespa.config.search.DispatchConfig;

/** Just a stub for now */
class MockDispatcher extends Dispatcher {

    public MockDispatcher() {
        super(new DispatchConfig(new DispatchConfig.Builder()), new FS4ResourcePool(1));
    }

    public void fill(Result result, String summaryClass) {
    }

}
