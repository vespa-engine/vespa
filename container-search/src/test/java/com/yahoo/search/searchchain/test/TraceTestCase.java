// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchchain.test;

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.Chain;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.yolean.trace.TraceNode;
import com.yahoo.yolean.trace.TraceVisitor;
import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Tests tracing scenarios where traces from multiple executions over the same query are involved.
 *
 * @author bratseth
 */
public class TraceTestCase {

    @Test
    public void testTracingOnCorrectAPIUseNonParallel() {
        assertTracing(true,false);
    }

    @Test
    public void testTracingOnIncorrectAPIUseNonParallel() {
        assertTracing(false,false);
    }

    @Test
    public void testTracingOnCorrectAPIUseParallel() {
        assertTracing(true, true);
    }

    @Test
    public void testTracingOnIncorrectAPIUseParallel() {
        assertTracing(false,true);
    }
    
    @Test 
    public void testTraceInvocationsUnfillableHits() {
        final int traceLevel = 5;
        Query query = new Query("?tracelevel=" + traceLevel);
        Chain<Searcher> forkingChain = new Chain<>(new Tracer("tracer1"), 
                                                   new Tracer("tracer2"), 
                                                   new Backend("backend1", false));
        Execution execution = new Execution(forkingChain, Execution.Context.createContextStub());
        Result result = execution.search(query);
        execution.fill(result, "mySummary");

        Iterator<String> trace = collectTrace(query).iterator();
        assertEquals("(level start)", trace.next());
        assertEquals("  No query profile is used", trace.next());
        trace.next(); // (properties trace: not checked)
        assertEquals("  (level start)", trace.next());
        assertEquals("    Invoke searcher 'tracer1'", trace.next());
        assertEquals("    During tracer1: 0", trace.next());
        assertEquals("    Invoke searcher 'tracer2'", trace.next());
        assertEquals("    During tracer2: 0", trace.next());
        assertEquals("    Invoke searcher 'backend1'", trace.next());
        assertEquals("    Return searcher 'backend1'", trace.next());
        assertEquals("    Return searcher 'tracer2'", trace.next());
        assertEquals("    Return searcher 'tracer1'", trace.next());
        assertEquals("    Invoke fill(mySummary) on searcher 'tracer1'", trace.next());
        assertEquals("    Ignoring fill(mySummary): Hits are unfillable: result.hits().getFilled()=null", trace.next());
        assertEquals("    Return fill(mySummary) on searcher 'tracer1'", trace.next());
        assertEquals("  (level end)", trace.next());
        assertEquals("(level end)", trace.next());
        assertFalse(trace.hasNext());
    }

    @Test
    public void testTraceInvocationsFillableHits() {
        final int traceLevel = 5;
        Query query = new Query("?tracelevel=" + traceLevel);
        Chain<Searcher> forkingChain = new Chain<>(new Tracer("tracer1"),
                                                   new Tracer("tracer2"),
                                                   new Backend("backend1", true));
        Execution execution = new Execution(forkingChain, Execution.Context.createContextStub());
        Result result = execution.search(query);
        execution.fill(result, "mySummary");

        Iterator<String> trace = collectTrace(query).iterator();
        assertEquals("(level start)", trace.next());
        assertEquals("  No query profile is used", trace.next());
        trace.next(); // (properties trace: not checked)
        assertEquals("  (level start)", trace.next());
        assertEquals("    Invoke searcher 'tracer1'", trace.next());
        assertEquals("    During tracer1: 0", trace.next());
        assertEquals("    Invoke searcher 'tracer2'", trace.next());
        assertEquals("    During tracer2: 0", trace.next());
        assertEquals("    Invoke searcher 'backend1'", trace.next());
        assertEquals("    Return searcher 'backend1'", trace.next());
        assertEquals("    Return searcher 'tracer2'", trace.next());
        assertEquals("    Return searcher 'tracer1'", trace.next());
        assertEquals("    Invoke fill(mySummary) on searcher 'tracer1'", trace.next());
        assertEquals("    Invoke fill(mySummary) on searcher 'tracer2'", trace.next());
        assertEquals("    Invoke fill(mySummary) on searcher 'backend1'", trace.next());
        assertEquals("    Return fill(mySummary) on searcher 'backend1'", trace.next());
        assertEquals("    Return fill(mySummary) on searcher 'tracer2'", trace.next());
        assertEquals("    Return fill(mySummary) on searcher 'tracer1'", trace.next());
        assertEquals("  (level end)", trace.next());
        assertEquals("(level end)", trace.next());
        assertFalse(trace.hasNext());
    }

    private void assertTracing(boolean carryOverContext, boolean parallel) {
        Query query = new Query("?tracelevel=1");
        query.trace("Before execution",1);
        Chain<Searcher> forkingChain = new Chain<>(new Tracer("forker"), 
                                                   new Forker(carryOverContext, parallel, 
                                                              new Tracer("branch 1") ,
                                                              new Tracer("branch 2")));
        new Execution(forkingChain, Execution.Context.createContextStub()).search(query);

        if (carryOverContext)
            assertTraceWithChildExecutionMessages(query);
        else if (parallel)
            assertTrace(query);
        else
            assertIncorrectlyNestedTrace(query);

        assertCorrectRendering(query);
    }

    // The valid and usual trace
    private void assertTraceWithChildExecutionMessages(Query query) {
        Iterator<String> trace = collectTrace(query).iterator();
        assertEquals("(level start)", trace.next());
        assertEquals("  No query profile is used", trace.next());
        assertEquals("  Before execution", trace.next());
        assertEquals("  (level start)", trace.next());
        assertEquals("    During forker: 0", trace.next());
        assertEquals("    (level start)", trace.next());
        assertEquals("      During branch 1: 0", trace.next());
        assertEquals("    (level end)", trace.next());
        assertEquals("    (level start)", trace.next());
        assertEquals("      During branch 2: 0", trace.next());
        assertEquals("    (level end)", trace.next());
        assertEquals("  (level end)", trace.next());
        assertEquals("(level end)", trace.next());
        assertFalse(trace.hasNext());
    }

    // With incorrect API usage and query cloning (in parallel use) we get a valid trace
    // where the message of the execution subtrees is empty rather than "child execution". This is fine.
    private void assertTrace(Query query) {
        Iterator<String> trace=collectTrace(query).iterator();
        assertEquals("(level start)", trace.next());
        assertEquals("  No query profile is used", trace.next());
        assertEquals("  Before execution", trace.next());
        assertEquals("  (level start)", trace.next());
        assertEquals("    During forker: 0", trace.next());
        assertEquals("    (level start)", trace.next());
        assertEquals("      During branch 1: 0", trace.next());
        assertEquals("    (level end)", trace.next());
        assertEquals("    (level start)", trace.next());
        assertEquals("      During branch 2: 0", trace.next());
        assertEquals("    (level end)", trace.next());
        assertEquals("  (level end)", trace.next());
        assertEquals("(level end)", trace.next());
        assertFalse(trace.hasNext());
    }

    // With incorrect usage and no query cloning the trace nesting becomes incorrect
    // but all the trace messages are present.
    private void assertIncorrectlyNestedTrace(Query query) {
        Iterator<String> trace=collectTrace(query).iterator();
        assertEquals("(level start)", trace.next());
        assertEquals("  No query profile is used", trace.next());
        assertEquals("  Before execution", trace.next());
        assertEquals("  (level start)", trace.next());
        assertEquals("    During forker: 0", trace.next());
        assertEquals("    (level start)", trace.next());
        assertEquals("      During branch 1: 0", trace.next());
        assertEquals("      (level start)", trace.next());
        assertEquals("        During branch 2: 0", trace.next());
        assertEquals("      (level end)", trace.next());
        assertEquals("    (level end)", trace.next());
        assertEquals("  (level end)", trace.next());
        assertEquals("(level end)", trace.next());
        assertFalse(trace.hasNext());
    }

    private void assertCorrectRendering(Query query) {
        try {
            StringWriter writer = new StringWriter();
            query.getContext(false).render(writer);
            String expected =
                    "<meta type=\"context\">\n" +
                    "\n" +
                    "  <p>No query profile is used</p>\n" +
                    "\n" +
                    "  <p>Before execution</p>\n" +
                    "\n" +
                    "  <p>\n" +
                    "    <p>During forker: 0";
            assertEquals(expected, writer.toString().substring(0, expected.length()));
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> collectTrace(Query query) {
        TraceCollector collector = new TraceCollector();
        query.getContext(false).getTrace().accept(collector);
        return collector.trace();
    }

    private static class TraceCollector extends TraceVisitor {

        private List<String> trace = new ArrayList<>();
        private StringBuilder indent = new StringBuilder();

        @Override
        public void entering(TraceNode node) {
            trace.add(indent + "(level start)");
            indent.append("  ");
        }

        @Override
        public void leaving(TraceNode end) {
            indent.setLength(indent.length() - 2);
            trace.add(indent + "(level end)");
        }

        @Override
        public void visit(TraceNode node) {
            if (node.isRoot()) return;
            if (node.payload() == null) return;
            trace.add(indent + node.payload().toString());
        }

        public List<String> trace() { return trace; }
    }

    private static class Tracer extends Searcher {

        private String name;
        private int counter = 0;

        public Tracer(String name) {
            super(new ComponentId(name));
            this.name = name;
        }

        @Override
        public Result search(Query query, Execution execution) {
            query.trace("During " + name + ": " + (counter++),1);
            return execution.search(query);
        }
    }

    private static class Forker extends Searcher {

        private List<Searcher> branches;

        /** If true, this is using the api as recommended, if false, it is not */
        private boolean carryOverContext;

        /** If true, simulate parallel execution by cloning the query */
        private boolean parallel;

        public Forker(boolean carryOverContext, boolean parallel, Searcher ... branches) {
            this.carryOverContext = carryOverContext;
            this.parallel = parallel;
            this.branches = Arrays.asList(branches);
        }

        @SuppressWarnings("deprecation")
        @Override
        public Result search(Query query, Execution execution) {
            Result result = execution.search(query);
            for (Searcher branch : branches) {
                Query branchQuery = parallel ? query.clone() : query;
                Result branchResult =
                        ( carryOverContext ? new Execution(branch, execution.context()) : 
                                             new Execution(branch, Execution.Context.createContextStub())).search(branchQuery);
                result.hits().add(branchResult.hits());
                result.mergeWith(branchResult);
            }
            return result;
        }

    }

    private static class Backend extends Searcher {

        private final boolean fillableHits;
        
        public Backend(String name, boolean fillableHits) {
            super(new ComponentId(name));
            this.fillableHits = fillableHits;
        }

        @Override
        public Result search(Query query, Execution execution) {
            Result result = execution.search(query);
            Hit hit0 = new Hit("hit:0");
            Hit hit1 = new Hit("hit:1");
            if (fillableHits) {
                hit0.setFillable();
                hit1.setFillable();
            }
            result.hits().add(hit0);
            result.hits().add(hit1);
            return result;
        }
    }

}
