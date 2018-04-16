// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.context.test;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.yahoo.processing.execution.Execution;
import com.yahoo.search.Query;
import com.yahoo.search.query.context.QueryContext;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class LoggingTestCase {

    @Test
    public void testLogging() {
        Query query=new Query();
        QueryContext queryContext = query.getContext(true);
        queryContext.logValue("a","a1");
        queryContext.trace("first message", 2);
        queryContext.logValue("a","a2");
        queryContext.logValue("b","b1");
        QueryContext h2 = query.clone().getContext(true);
        h2.logValue("b","b2");
        h2.trace("second message", 2);
        h2.logValue("b","b3");
        queryContext.logValue("b","b4");
        QueryContext h3 = query.clone().getContext(true);
        h3.logValue("b","b5");
        h3.logValue("c","c1");
        h3.trace("third message", 2);
        h2.logValue("c","c2");
        queryContext.trace("fourth message", 2);
        queryContext.logValue("d","d1");
        h2.trace("fifth message", 2);
        h2.logValue("c","c3");
        queryContext.logValue("c","c4");

        // Assert that all of the above is in the log, in some undefined order
        Set<String> logValues=new HashSet<>();
        for (Iterator<Execution.Trace.LogValue> logValueIterator=queryContext.logValueIterator(); logValueIterator.hasNext(); )
            logValues.add(logValueIterator.next().toString());
        assertEquals(12,logValues.size());
        assertTrue(logValues.contains("a=a1"));
        assertTrue(logValues.contains("a=a2"));
        assertTrue(logValues.contains("b=b1"));
        assertTrue(logValues.contains("b=b2"));
        assertTrue(logValues.contains("b=b3"));
        assertTrue(logValues.contains("b=b4"));
        assertTrue(logValues.contains("b=b5"));
        assertTrue(logValues.contains("c=c1"));
        assertTrue(logValues.contains("c=c2"));
        assertTrue(logValues.contains("d=d1"));
        assertTrue(logValues.contains("c=c3"));
        assertTrue(logValues.contains("c=c4"));
    }

}
