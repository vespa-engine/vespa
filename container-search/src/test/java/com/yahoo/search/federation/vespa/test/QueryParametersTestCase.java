// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.vespa.test;

import com.yahoo.search.Query;
import com.yahoo.search.federation.vespa.VespaSearcher;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Tests that source and backend specific parameters from the query are added correctly to the backend requests
 *
 * @author bratseth
 */
public class QueryParametersTestCase {

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

        VespaSearcher searcher=new VespaSearcher("testProvider","",0,"");
        Map<String,String> parameters=searcher.getQueryMap(query);
        searcher.deconstruct();

        assertEquals(9, parameters.size()); // 5 standard + the appropriate 4 of the above
        assertEquals(parameters.get("e"),"e-value");
        assertEquals(parameters.get("f.g"),"f.g-value");
        assertEquals(parameters.get("h"),"h-value");
        assertEquals(parameters.get("i.j"),"i.j-value");
    }

}

