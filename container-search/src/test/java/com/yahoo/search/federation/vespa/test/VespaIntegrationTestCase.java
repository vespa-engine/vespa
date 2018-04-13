// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.vespa.test;

import com.yahoo.component.chain.Chain;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.federation.vespa.VespaSearcher;
import com.yahoo.search.searchchain.Execution;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class VespaIntegrationTestCase {

    // TODO: Setup the answering vespa searcher from this test....
    @Test
    public void testIt() {
        if (System.currentTimeMillis() > 0) return;
        Chain<Searcher> chain = new Chain<>(new VespaSearcher("test","example.yahoo.com",19010,""));
        Result result = new Execution(chain, Execution.Context.createContextStub()).search(new Query("?query=test"));
        assertEquals(23, result.hits().size());
    }

}
