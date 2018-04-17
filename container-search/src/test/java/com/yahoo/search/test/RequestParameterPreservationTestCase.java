// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.test;

import com.yahoo.search.Query;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class RequestParameterPreservationTestCase {

    @Test
    public void testPreservation() {
        Query query=new Query("?query=test...&offset=15&hits=10");
        query.setWindow(25,13);
        assertEquals(25,query.getOffset());
        assertEquals(13,query.getHits());
        assertEquals("15", query.getHttpRequest().getProperty("offset"));
        assertEquals("10", query.getHttpRequest().getProperty("hits"));
        assertEquals("test...",query.getHttpRequest().getProperty("query"));
    }

}
