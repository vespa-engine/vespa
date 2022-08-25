// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.execution;

import com.yahoo.collections.Pair;
import com.yahoo.component.chain.Chain;
import com.yahoo.processing.Processor;
import com.yahoo.processing.Request;
import com.yahoo.processing.Response;
import com.yahoo.processing.execution.chain.ChainRegistry;
import com.yahoo.yolean.trace.TraceNode;
import com.yahoo.yolean.trace.TraceVisitor;

import java.util.Iterator;

/**
 * An execution of a chain. This keeps tracks of the progress of the execution and is called by the
 * processors (using {@link #process} to move the execution to the next one.
 *
 * @author bratseth
 */
public class Execution {

    /**
     * The index of the searcher in the chain which should be executed on the next call
     * An Execution instance contains the state of a chain execution by
     * providing a class stack for a chain - when a processor is called
     * (through this), it will increment the index of the processor to call next,
     * each time a processor returns (regardless of how) it will do the opposite.
     */
    private int processorIndex;

    private final Chain<? extends Processor> chain;

    private final Trace trace;

    private final Environment<? extends Processor> environment;

    /**
     * Creates an execution of a single processor
     *
     * @param processor the processor to execute in this
     * @param execution the parent execution of this
     */
    public Execution(Processor processor, Execution execution) {
        this(new Chain<>(processor), execution);
    }

    /** Creates an execution of a single processor which is not in the context of an existing execution */
    public static Execution createRoot(Processor processor, int traceLevel, Environment<? extends Processor> environment) {
        return createRoot(new Chain<>(processor), traceLevel, environment);
    }

    /**
     * Creates an execution which is not in the context of an existing execution
     *
     * @param chain the chain to execute
     * @param traceLevel the level to emit trace at
     * @param environment the execution environment to use
     */
    public static Execution createRoot(Chain<? extends Processor> chain, int traceLevel,
                                       Environment<? extends Processor> environment) {
        return new Execution(chain, 0, Trace.createRoot(traceLevel), environment);
    }

    /**
     * Creates an execution of a chain
     *
     * @param chain     the chain to execute in this, starting from the first processor
     * @param execution the parent execution of this
     */
    public Execution(Chain<? extends Processor> chain, Execution execution) {
        this(chain, 0, execution.trace().createChild(), execution.environment().nested());
    }

    /**
     * Creates an execution from another.  This execution will start at the next processor of the
     * given execution. The given execution can continue independently of this.
     */
    public Execution(Execution startPoint) {
        this(startPoint.chain, startPoint.processorIndex, startPoint.trace.createChild(), startPoint.environment().nested());
    }

    /**
     * Creates a new execution by setting the internal state directly.
     *
     * @param chain       the chain to execute
     * @param startIndex  the start index into that chain
     * @param trace       the context <b>of this</b>. If this is created from an existing execution/context,
     *                    be sure to do <code> new Context&lt;COMPONENT&gt;(startPoint.context)</code> first!
     * @param environment the static execution environment to use
     */
    protected Execution(Chain<? extends Processor> chain, int startIndex, Trace trace, Environment<? extends Processor> environment) {
        if (chain == null) throw new NullPointerException("Chain cannot be null");
        this.chain = chain;
        this.processorIndex = startIndex;
        this.trace = trace;
        this.environment = environment;
    }

    /**
     * Calls process on the next processor in this chain. If there is no next, an empty response is returned.
     */
    public Response process(Request request) {
        Processor processor = next();
        if (processor == null)
            return defaultResponse(request);

        Response response = null;
        try {
            nextProcessor();
            onInvoking(request, processor);
            response = processor.process(request, this);
            if (response == null)
                throw new NullPointerException(processor + " returned null, not a Response object");
            return response;
        } finally {
            previousProcessor();
            onReturning(request, processor, response);
        }
    }

    /**
     * Returns the index into the chain of processors which is currently next
     */
    protected int nextIndex() {
        return processorIndex;
    }

    /**
     * A hook called when a processor is to be invoked. Overriding methods must call super.onInvoking
     */
    protected void onInvoking(Request request, Processor next) {
        if (Trace.Level.Step.includes(trace.getTraceLevel()) || trace.getForceTimestamps()) {
            int traceAt = trace.getForceTimestamps() ? 1 : trace.getTraceLevel();
            trace.trace("Invoke " + next, traceAt);
        }
        if (Trace.Level.Dependencies.includes(trace.getTraceLevel()))
            trace.trace(next.getId() + " " + next.getDependencies().toString(), trace.getTraceLevel());
    }

    /**
     * A hook called when a processor returns, either normally or by throwing.
     * Overriding methods must call super.onReturning
     *
     * @param request   the processing request
     * @param processor the processor which returned
     * @param response  the response returned, or null if the processor returned by throwing
     */
    protected void onReturning(Request request, Processor processor, Response response) {
        if (Trace.Level.Step.includes(trace.getTraceLevel()) || trace.getForceTimestamps()) {
            int traceAt = trace.getForceTimestamps() ? 1 : trace.getTraceLevel();
            trace.trace("Return " + processor, traceAt);
        }
    }

    /** Move this execution to the previous processor */
    protected void previousProcessor() {
        processorIndex--;
    }

    /** Move this execution to the next processor */
    protected void nextProcessor() {
        processorIndex++;
    }

    /** Returns the next searcher to be invoked in this chain, or null if we are at the last */
    protected Processor next() {
        if (chain.components().size() <= processorIndex) return null;
        return chain.components().get(processorIndex);
    }

    /** Returns the chain this executes */
    public Chain<? extends Processor> chain() {
        return chain;
    }

    /**
     * Creates the default response to return from this kind of execution when there are no further processors.
     * If this is overridden, make sure to propagate any freezeListener from this to the returned response
     * top-level DataList.
     */
    protected Response defaultResponse(Request request) {
        return new Response(request);
    }

    public String toString() {
        return "execution of chain '" + chain.getId() + "'";
    }

    public Trace trace() {
        return trace;
    }

    public Environment<? extends Processor> environment() {
        return environment;
    }

    /**
     * Holds the static execution environment for the duration of an execution
     */
    public static class Environment<COMPONENT extends Processor> {

        private final ChainRegistry<COMPONENT> chainRegistry;

        /**
         * Creates an empty environment. Only useful for some limited testing
         */
        public static <C extends Processor> Environment<C> createEmpty() {
            return new Environment<>(new ChainRegistry<>());
        }

        /**
         * Returns an environment for an execution spawned from the execution having this environment.
         */
        public Environment<COMPONENT> nested() {
            return this; // this is immutable, subclasses might want to do something else though
        }

        /**
         * Creates a new environment
         */
        public Environment(ChainRegistry<COMPONENT> chainRegistry) {
            this.chainRegistry = chainRegistry;
        }

        /**
         * Returns the processing chain registry of this execution environment.
         * The registry may be empty, but never null.
         */
        public ChainRegistry<COMPONENT> chainRegistry() {
            return chainRegistry;
        }

    }

    /**
     * Tre trace of this execution. This is a facade into a node in the larger trace tree which captures
     * the information about all executions caused by some request
     *
     * @author bratseth
     */
    public static class Trace {

        /**
         * The node in the trace tree capturing this execution
         */
        private final TraceNode traceNode;

        /**
         * The highest level of tracing this should record
         */
        private int traceLevel;

        /**
         * If true, do timing logic, even though trace level is low.
         */
        private boolean forceTimestamps;

        /**
         * Creates an empty root trace with a given level of tracing
         */
        public static Trace createRoot(int traceLevel) {
            return new Trace(traceLevel, new TraceNode(null, timestamp(traceLevel, false)), false);
        }

        /**
         * Creates a trace node below a parent
         */
        public Trace createChild() {
            TraceNode child = new TraceNode(null, timestamp(traceLevel, forceTimestamps));
            traceNode.add(child);
            return new Trace(getTraceLevel(), child, forceTimestamps);
        }

        /**
         * Creates a new instance by assigning the internal state of this directly
         */
        private Trace(int traceLevel, TraceNode traceNode, boolean forceTimestamps) {
            this.traceLevel = traceLevel;
            this.traceNode = traceNode;
            this.forceTimestamps = forceTimestamps;
        }

        /**
         * Returns the maximum trace level this will record
         */
        public int getTraceLevel() {
            return traceLevel;
        }

        /**
         * Sets the maximum trace level this will record
         */
        public void setTraceLevel(int traceLevel) {
            this.traceLevel = traceLevel;
        }

        public void setForceTimestamps(boolean forceTimestamps) {
            this.forceTimestamps = forceTimestamps;
        }

        public boolean getForceTimestamps() {
            return forceTimestamps;
        }

        /**
         * Adds a trace message to this trace, if this trace has at most the given trace level
         */
        public void trace(String message, int traceLevel) {
            trace((Object)message, traceLevel);
        }
        public void trace(Object message, int traceLevel) {
            if (this.traceLevel >= traceLevel) {
                traceNode.add(new TraceNode(message, timestamp(traceLevel, forceTimestamps)));
            }
        }

        /**
         * Adds a key-value which will be logged to the access log of this request.
         * Multiple values may be set to the same key. A value cannot be removed once set,
         * but it can be overwritten by adding another value for the same key.
         */
        public void logValue(String key, String value) {
            traceNode.add(new TraceNode(new LogValue(key, value), 0));
        }

        /**
         * Returns the values that should be written to the access log set in the entire trace node tree
         */
        public Iterator<LogValue> logValueIterator() {
            return traceNode.root().descendants(LogValue.class).iterator();
        }

        /**
         * Visits the entire trace tree
         *
         * @return the argument visitor for convenience
         */
        public <VISITOR extends TraceVisitor> VISITOR accept(VISITOR visitor) {
            return traceNode.root().accept(visitor);
        }

        /**
         * Adds a property key-value to this trace.
         * Values are looked up by reverse depth-first search in the trace node tree.
         *
         * @param name  the name of the property
         * @param value the value of the property, or null to set this property to null
         */
        public void setProperty(String name, Object value) {
            traceNode.add(new TraceNode(new Pair<>(name, value), 0));
        }

        /**
         * Returns a property set anywhere in the trace tree this points to.
         * Note that even though this call is itself "thread robust", the object values returned
         * may in some scenarios not be written behind a synchronization barrier, so when accessing
         * objects which are not inherently thread safe, synchronization should be considered.
         * <p>
         * This method have a time complexity which is proportional to
         * the number of trace nodes in the tree
         *
         * @return the value of this property, or null if none
         */
        public Object getProperty(String name) {
            return accept(new PropertyValueVisitor(name)).foundValue();
        }

        /**
         * Returns the trace node peer of this
         */
        public TraceNode traceNode() {
            return traceNode;
        }

        /**
         * Returns a short string description of this
         */
        @Override
        public String toString() {
            return "trace: " + traceNode;
        }

        private static long timestamp(int traceLevel, boolean forceTimestamps) {
            return (forceTimestamps || Level.Timestamp.includes(traceLevel)) ? System.currentTimeMillis() : 0;
        }

        /**
         * Visits all trace nodes to collect the last set value of a particular property in a trace tree
         */
        private static class PropertyValueVisitor extends TraceVisitor {

            /**
             * The name of the property to find
             */
            private final String name;
            private Object foundValue = null;

            public PropertyValueVisitor(String name) {
                this.name = name;
            }

            @Override
            public void visit(TraceNode node) {
                if (node.payload() == null) return;
                if (!(node.payload() instanceof Pair property)) return;

                if (!property.getFirst().equals(name)) return;
                foundValue = property.getSecond();
            }

            public Object foundValue() {
                return foundValue;
            }

        }

        /**
         * An immutable access log value added to the trace
         */
        public static class LogValue {

            private final String key;
            private final String value;

            public LogValue(String key, String value) {
                this.key = key;
                this.value = value;
            }

            public String getKey() {
                return key;
            }

            public String getValue() {
                return value;
            }

            @Override
            public String toString() {
                return key + "=" + value;
            }

        }

        /**
         * Defines what information is added at which trace level
         */
        public enum Level {

            /**
             * Every processing step initiated is traced
             */
            Step(4),
            /**
             * All trace messages are timestamped
             */
            Timestamp(6),
            /**
             * The before/after dependencies of each processing step is traced on every invocation
             */
            Dependencies(7);

            /**
             * The smallest trace level at which this information will be traced
             */
            private final int value;

            Level(int value) {
                this.value = value;
            }

            public int value() {
                return value;
            }

            /**
             * Returns whether this level includes the given level, i.e whether traceLevel is this.value() or more
             */
            public boolean includes(int traceLevel) {
                return traceLevel >= this.value();
            }

        }
    }
}
