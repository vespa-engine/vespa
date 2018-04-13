// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.http;

import com.yahoo.component.ComponentId;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.statistics.Statistics;
import com.yahoo.vespa.defaults.Defaults;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Tests that source and backend specific parameters from the query are added correctly to the backend requests
 *
 * @author bratseth
 */
public class QueryParametersTestCase {

    @Test
    public void testQueryParameters() {
        Query query=new Query();
        query.properties().set("a","a-value");
        query.properties().set("b.c","b.c-value");
        query.properties().set("source.otherSource.d","d-value");
        query.properties().set("source.testSource.e","e-value");
        query.properties().set("source.testSource.f.g","f.g-value");
        query.properties().set("provider.testProvider.h","h-value");
        query.properties().set("provider.testProvider.i.j","i.j-value");

        query.properties().set("sourceName","testSource"); // Done by federation searcher
        query.properties().set("providerName","testProvider"); // Done by federation searcher

        TestHttpProvider searcher=new TestHttpProvider();
        Map<String,String> parameters=searcher.getQueryMap(query);
        searcher.deconstruct();

        assertEquals(4,parameters.size()); // the appropriate 4 of the above
        assertEquals(parameters.get("e"),"e-value");
        assertEquals(parameters.get("f.g"),"f.g-value");
        assertEquals(parameters.get("h"),"h-value");
        assertEquals(parameters.get("i.j"),"i.j-value");
    }

    public static class TestHttpProvider extends HTTPProviderSearcher {

        public TestHttpProvider() {
            super(new ComponentId("test"), Collections.singletonList(new Connection("host", Defaults.getDefaults().vespaWebServicePort())), "path", Statistics.nullImplementation);
        }

        @Override
        public Map<String, String> getCacheKey(Query q) {
            return Collections.singletonMap("nocaching", String.valueOf(Math.random()));
        }

        @Override
        protected void fill(Result result, String summaryClass, Execution execution, Connection connection) {
        }

    }

}

