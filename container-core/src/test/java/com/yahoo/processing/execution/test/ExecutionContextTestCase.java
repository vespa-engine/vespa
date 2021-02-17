// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.execution.test;

import com.yahoo.component.chain.Chain;
import com.yahoo.processing.Processor;
import com.yahoo.processing.execution.Execution;
import com.yahoo.processing.test.ProcessorLibrary;
import org.junit.Test;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class ExecutionContextTestCase {

    private final Chain<Processor> chain = new Chain<Processor>(new ProcessorLibrary.DataSource());

    /** Tests combined use of trace messages, context values and access log entries */
    @Test
    public void testtrace() {
        Execution execution1=Execution.createRoot(chain,2,Execution.Environment.createEmpty());
        execution1.trace().setProperty("a","a1");
        execution1.trace().logValue("a","a1");
        execution1.trace().trace("root 1", 2);
        execution1.trace().setProperty("a","a2");
        execution1.trace().setProperty("b","b1");
        execution1.trace().logValue("a","a2");
        execution1.trace().logValue("b","b1");

        Execution execution2=new Execution(chain,execution1);
        execution2.trace().setProperty("b","b2");
        execution2.trace().logValue("b","b2");
        execution2.trace().trace("  child-1 1", 2);
        execution2.trace().setProperty("b", "b3");
        execution2.trace().logValue("b","b3");

        execution1.trace().setProperty("b","b4");
        execution1.trace().logValue("b","b4");

        Execution execution3=new Execution(chain,execution1);
        execution3.trace().setProperty("b","b5");
        execution3.trace().setProperty("c","c1");
        execution3.trace().logValue("b","b5");
        execution3.trace().logValue("c","c1");
        execution3.trace().trace("  child-2 1", 2);

        execution2.trace().setProperty("c","c2");
        execution2.trace().logValue("c","c2");

        execution1.trace().trace("root 2", 2);
        execution3.trace().setProperty("d", "d1");
        execution1.trace().logValue("d","d1");

        execution2.trace().trace("  child-1 2", 2);
        execution2.trace().setProperty("c", "c3");
        execution2.trace().logValue("c","c3");

        execution1.trace().setProperty("c","c4");
        execution1.trace().logValue("c","c4");

        Iterator<String> traceIterator=execution1.trace().traceNode().root().descendants(String.class).iterator();
        assertEquals("root 1",traceIterator.next());
        assertEquals("  child-1 1",traceIterator.next());
        assertEquals("  child-1 2",traceIterator.next());
        assertEquals("  child-2 1",traceIterator.next());
        assertEquals("root 2",traceIterator.next());
        assertFalse(traceIterator.hasNext());

        // Verify context variables
        assertEquals("a2", execution1.trace().getProperty("a"));
        assertEquals("b5", execution1.trace().getProperty("b"));
        assertEquals("c4", execution1.trace().getProperty("c"));
        assertEquals("d1", execution1.trace().getProperty("d"));
        assertNull(execution1.trace().getProperty("e"));

        // Verify access log
        Set<String> logValues=new HashSet<>();
        for (Iterator<Execution.Trace.LogValue> logValueIterator=execution1.trace().logValueIterator(); logValueIterator.hasNext(); )
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
