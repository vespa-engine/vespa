// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.properties.test;

import com.yahoo.search.Query;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.test.QueryTestCase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests that dimension arguments in queries are transferred correctly to dimension values
 *
 * @author bratseth
 */
public class RequestContextPropertiesTestCase {

    @Test
    public void testIt() {
        QueryProfile p=new QueryProfile("test");
        p.setDimensions(new String[] {"x"});
        p.set("a","a-default", (QueryProfileRegistry)null);
        p.set("a","a-x1",new String[] {"x1"}, null);
        p.set("a","a-+x1",new String[] {"+x1"}, null);
        Query q1 = new Query(QueryTestCase.httpEncode("?query=foo"), p.compile(null));
        assertEquals("a-default",q1.properties().get("a"));
        Query q2 = new Query(QueryTestCase.httpEncode("?query=foo&x=x1"),p.compile(null));
        assertEquals("a-x1",q2.properties().get("a"));
        Query q3 = new Query(QueryTestCase.httpEncode("?query=foo&x=+x1"),p.compile(null));
        assertEquals("a-+x1",q3.properties().get("a"));
    }

}
