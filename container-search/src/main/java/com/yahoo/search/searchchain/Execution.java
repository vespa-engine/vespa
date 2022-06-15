// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchchain;

import com.yahoo.component.chain.Chain;
import com.yahoo.language.Linguistics;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.Ping;
import com.yahoo.prelude.Pong;
import com.yahoo.language.process.SpecialTokenRegistry;
import com.yahoo.prelude.fastsearch.VespaBackEndSearcher;
import com.yahoo.processing.Processor;
import com.yahoo.processing.Request;
import com.yahoo.processing.Response;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.cluster.PingableSearcher;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.rendering.RendererRegistry;
import com.yahoo.search.statistics.TimeTracker;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * <p>An execution of a search chain. This keeps track of the call state for an execution (in the calling thread)
 * of the searchers of a search chain.</p>
 *
 * <p>To execute a search chain, simply do
 * <pre>
 *     Result result = new Execution(mySearchChain, execution.context()).search(query)
 * </pre>
 *
 *
 * <p>See also {@link AsyncExecution}, which performs an execution in a separate thread than the caller.</p>
 *
 * <p>Execution instances should not be reused for multiple separate executions.</p>
 *
 * @author bratseth
 */
public class Execution extends com.yahoo.processing.execution.Execution {

    /**
     * The execution context is the search chain's current view of the indexes,
     * search chain registrys, etc. Searcher instances may set values here to
     * change the behavior of the rest of the search chain.
     * <p>
     * The Context class simply carries a set of objects which define the
     * environment for the search. <b>Important:</b> All objects available through context need to
     * be either truly immutable or support the freeze pattern.
     * <p>
     * If you are implementing a searcher where you need to create a new Context
     * instance to create an Execution, you should use the context from the
     * execution the searcher was invoked from. You can also copy
     * (Context.shallowCopy()) the incoming context if it is necessary to do
     * more. In other words, a minimal example would be:<br>
     * new Execution(searchChain, execution.context())
     */
    public static final class Context {

        /** Whether the search should perform detailed diagnostics. */
        private boolean detailedDiagnostics = false;

        /** Whether the container was considered to be in a breakdown state when this query started. */
        private boolean breakdown = false;

        /**
         * The search chain registry current when this execution was created, or
         * when the registry was first accessed, or null if it was not set on
         * creation or has been accessed yet. No setter method is intentional.
         */
        private SearchChainRegistry searchChainRegistry = null;

        private IndexFacts indexFacts = null;

        private SchemaInfo schemaInfo = SchemaInfo.empty();

        /** The current set of special tokens */
        private SpecialTokenRegistry tokenRegistry = null;

        /** The current template registry */
        private RendererRegistry rendererRegistry = null;

        /** The current linguistics */
        private Linguistics linguistics = null;

        private Executor executor;

        /** Always set if this context belongs to an execution, never set if it does not. */
        private final Execution owner;

        // Please don't add more constructors to the public interface of Context
        // unless the constructor is reasonably safe for an inexperienced user
        // in a production setting. Since queries blow up in a spectacular
        // fashion if Context is in a bad state, the Context() constructor is
        // package private.

        /** Create a context used to carry state into another context */
        Context() { this.owner = null; }

        /** Create a context which belongs to an execution */
        Context(Execution owner) { this.owner = owner; }

        /**
         * Creates a context from arguments, all of which may be null, though
         * this can be risky. If you are doing this outside a test, it is
         * usually better to do something like execution.context().shallowCopy()
         * instead, and then set the fields you need to change. It is also safe
         * to use the context from the incoming execution directly. In other
         * words, a plug-in writer should practically never construct a Context
         * instance directly.
         * <p>
         * This context is never attached to an execution but is used to carry state into
         * another context.
         */
        public Context(SearchChainRegistry searchChainRegistry, IndexFacts indexFacts, SchemaInfo schemaInfo,
                       SpecialTokenRegistry tokenRegistry, RendererRegistry rendererRegistry, Linguistics linguistics,
                       Executor executor) {
            owner = null;
            // Four methods need to be updated when adding something:
            // fill(Context), populateFrom(Context), equals(Context) and,
            // obviously, the most complete constructor.
            this.searchChainRegistry = searchChainRegistry;
            this.indexFacts = indexFacts;
            this.schemaInfo = Objects.requireNonNull(schemaInfo);
            this.tokenRegistry = tokenRegistry;
            this.rendererRegistry = rendererRegistry;
            this.linguistics = linguistics;
            this.executor = Objects.requireNonNull(executor, "The executor cannot be null");
        }

        /** Creates a Context instance where everything except the given arguments is empty. This is for unit testing.*/
        public static Context createContextStub() {
            return createContextStub(null, null, SchemaInfo.empty(), null);
        }

        /** Creates a Context instance where everything except the given arguments is empty. This is for unit testing.*/
        public static Context createContextStub(SearchChainRegistry searchChainRegistry) {
            return createContextStub(searchChainRegistry, null, SchemaInfo.empty(), null);
        }

        /** Creates a Context instance where everything except the given arguments is empty. This is for unit testing.*/
        public static Context createContextStub(IndexFacts indexFacts) {
            return createContextStub(null, indexFacts, SchemaInfo.empty(), null);
        }

        /** Creates a Context instance where everything except the given arguments is empty. This is for unit testing.*/
        public static Context createContextStub(SchemaInfo schemaInfo) {
            return createContextStub(null, null, schemaInfo, null);
        }

        /** Creates a Context instance where everything except the given arguments is empty. This is for unit testing.*/
        public static Context createContextStub(SearchChainRegistry searchChainRegistry, IndexFacts indexFacts) {
            return createContextStub(searchChainRegistry, indexFacts, SchemaInfo.empty(), null);
        }

        /** Creates a Context instance where everything except the given arguments is empty. This is for unit testing.*/
        public static Context createContextStub(IndexFacts indexFacts, Linguistics linguistics) {
            return createContextStub(null, indexFacts, SchemaInfo.empty(), linguistics);
        }

        public static Context createContextStub(SearchChainRegistry searchChainRegistry,
                                                IndexFacts indexFacts,
                                                Linguistics linguistics) {
            return createContextStub(searchChainRegistry, indexFacts, SchemaInfo.empty(), linguistics);
        }

        /** Creates a Context instance where everything except the given arguments is empty. This is for unit testing.*/
        public static Context createContextStub(SearchChainRegistry searchChainRegistry,
                                                IndexFacts indexFacts,
                                                SchemaInfo schemaInfo,
                                                Linguistics linguistics) {
            return new Context(searchChainRegistry != null ? searchChainRegistry : new SearchChainRegistry(),
                               indexFacts != null ? indexFacts : new IndexFacts(),
                               schemaInfo,
                               null,
                               new RendererRegistry(Runnable::run),
                               linguistics != null ? linguistics : new SimpleLinguistics(),
                               Executors.newSingleThreadExecutor());
        }

        /**
         * Populate missing values in this from the given context.
         * Values which are non-null in this will not be overwritten.
         *
         * @param sourceContext the context from which to get the parameters
         */
        // TODO: Deprecate
        public void populateFrom(Context sourceContext) {
            // breakdown and detailedDiagnostics has no unset state, so they are always copied
            detailedDiagnostics = sourceContext.detailedDiagnostics;
            breakdown = sourceContext.breakdown;
            if (indexFacts == null)
                indexFacts = sourceContext.indexFacts;
            schemaInfo = sourceContext.schemaInfo;
            if (tokenRegistry == null)
                tokenRegistry = sourceContext.tokenRegistry;
            if (searchChainRegistry == null)
                searchChainRegistry = sourceContext.searchChainRegistry;
            if (rendererRegistry == null)
                rendererRegistry = sourceContext.rendererRegistry;
            if (linguistics == null)
                linguistics = sourceContext.linguistics;
            executor = sourceContext.executor; // executor will always either be the same, or we're in a test
        }

        /**
         * The brutal version of populateFrom().
         *
         * @param other a Context instance this will copy all state from
         */
        void fill(Context other) {
            searchChainRegistry = other.searchChainRegistry;
            indexFacts = other.indexFacts;
            schemaInfo = other.schemaInfo;
            tokenRegistry = other.tokenRegistry;
            rendererRegistry = other.rendererRegistry;
            detailedDiagnostics = other.detailedDiagnostics;
            breakdown = other.breakdown;
            linguistics = other.linguistics;
            executor = other.executor;
        }

        public boolean equals(Context other) {
            // equals() needs to be cheap, that's yet another reason we can only
            // allow immutables and frozen objects in the context
            return other.indexFacts == indexFacts
                   && other.schemaInfo == schemaInfo
                   && other.rendererRegistry == rendererRegistry
                   && other.tokenRegistry == tokenRegistry
                   && other.searchChainRegistry == searchChainRegistry
                   && other.detailedDiagnostics == detailedDiagnostics
                   && other.breakdown == breakdown
                   && other.linguistics == linguistics
                   && other.executor == executor;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(indexFacts,
                                          schemaInfo,
                                          rendererRegistry, tokenRegistry, searchChainRegistry,
                                          detailedDiagnostics, breakdown,
                                          linguistics,
                                          executor);
        }

        @Override
        public boolean equals(Object other) {
            if (other == null) return false;
            if (other.getClass() != Context.class) return false;
            return equals((Context) other);
        }

        /**
         * Standard shallow copy, the new instance will carry the same
         * references as this.
         *
         * @return a new instance which is a shallow copy
         */
        public Context shallowCopy() {
            Context c = new Context();
            c.fill(this);
            return c;
        }

        /**
         * This is used when building the Context stack. If Context has been
         * changed since last time, build a new object. Otherwise simply return
         * the previous snapshot.
         *
         * @param previous another Context instance to compare with
         * @return a copy of this, or previous
         */
        Context copyIfChanged(Context previous) {
            if (equals(previous)) {
                return previous;
            } else {
                return shallowCopy();
            }
        }

        /**
         * Returns information about the indexes specified by the search definitions
         * used in this system, or null if not know.
         */
        // TODO: Make a null default instance
        public IndexFacts getIndexFacts() {
            return indexFacts;
        }

        /**
         * Use this to override index settings for the searchers below
         * a given searcher, the easiest way to do this is to wrap the incoming
         * IndexFacts instance in a subclass. E.g.
         * execution.context().setIndexFacts(new WrapperClass(execution.context().getIndexFacts())).
         *
         * @param indexFacts an instance to override the following searcher's view of the indexes
         */
        public void setIndexFacts(IndexFacts indexFacts) {
            this.indexFacts = indexFacts;
        }

        /** Returns information about the schemas specified in this application. This is never null. */
        public SchemaInfo schemaInfo() { return schemaInfo; }

        /**
         * Returns the search chain registry to use with this execution. This is
         * a snapshot taken at creation of this execution, use
         * Context.shallowCopy() to get a correctly instantiated Context if
         * making a custom Context instance.
         */
        public SearchChainRegistry searchChainRegistry() { return searchChainRegistry; }

        /**
         * Returns the template registry to use with this execution. This is
         * a snapshot taken at creation of this execution.
         */
        public RendererRegistry rendererRegistry() { return rendererRegistry; }

        /** Returns the current set of special strings for the query tokenizer */
        public SpecialTokenRegistry getTokenRegistry() { return tokenRegistry; }

        /**
         * Wrapping the incoming special token registry and then setting the
         * wrapper as the token registry, can be used for changing the set of
         * special tokens used by succeeding searchers. E.g.
         * execution.context().setTokenRegistry(new WrapperClass(execution.context().getTokenRegistry())).
         *
         * @param tokenRegistry a new registry for overriding behavior of following searchers
         */
        public void setTokenRegistry(SpecialTokenRegistry tokenRegistry) { this.tokenRegistry = tokenRegistry; }

        public void setDetailedDiagnostics(boolean breakdown) { this.detailedDiagnostics = breakdown; }

        /**
         * The container has some internal diagnostics mechanisms which may be
         * costly, and therefore not active by default. Any general diagnostic
         * mechanism which should not be active be default, may inspect that
         * state here. If breakdown is assumed, a certain percentage of queries
         * will have this set automatically.
         *
         * @return whether components exposing different level of diagnostics
         *         should go for the most detailed level
         */
        public boolean getDetailedDiagnostics() { return detailedDiagnostics; }

        /**
         * If too many queries time out, the search handler will assume the
         * system is in a breakdown state. This state is propagated here.
         *
         * @return whether the system is assumed to be in a breakdown state
         */
        public boolean getBreakdown() { return breakdown; }

        public void setBreakdown(boolean breakdown) { this.breakdown = breakdown; }

        /**
         * Returns the {@link Linguistics} object assigned to this Context. This object provides access to all the
         * linguistic-related APIs, and comes pre-configured with the Execution given.
         */
        public Linguistics getLinguistics() { return linguistics; }

        public void setLinguistics(Linguistics linguistics) { this.linguistics = linguistics; }

        /**
         * Returns the executor that should be used to execute tasks as part of this execution.
         * This is never null but will be an executor that runs a single thread if none is passed to this.
         */
        public Executor executor() { return executor; }

        /** Creates a child trace if this has an owner, or a root trace otherwise */
        private Trace createChildTrace() {
            return owner!=null ? owner.trace().createChild() : Trace.createRoot(0);
        }

        /** Creates a child environment if this has an owner, or a root environment otherwise */
        private Environment createChildEnvironment() {
            return owner!=null ? owner.environment().nested() : Execution.Environment.<Searcher>createEmpty();
        }

    }

    /**
     * The index of where in the chain this Execution has its initial entry point.
     * This is needed because executions can be started from the middle of other executions.
     */
    private final int entryIndex;

    /** Time spent in each state of filling, searching or pinging. */
    private final TimeTracker timer;

    /** A searcher's view of state external to the search chain. */
    // Note that the context plays the same role as the Environment of the super.Execution
    // (although complicated by the need for stack-like behavior on changes).
    // We might want to unify those at some point.
    private final Context context = new Context(this);

    /**
     * Array for hiding context changes done in search by searcher following
     * another.
     */
    private final Context[] contextCache;

    /**
     * <p>
     * Creates an execution from another. This execution will start at the
     * <b>current next searcher</b> in the given execution, rather than at the
     * start.
     * </p>
     *
     * <p>
     * The relevant state of the given execution is copied before this method
     * returns - the argument execution can then be reused for any other
     * purpose.
     * </p>
     */
    public Execution(Execution execution) {
        this(execution.chain(), execution.context, execution.nextIndex());
    }

    /** Creates an which executes nothing */
    public Execution(Context context) {
        this(new Chain<>(), context);
    }

    /**
     * The usually best way of creating a new execution for a search chain. This
     * is the one suitable for a production environment. It is safe to use the
     * incoming context from the search directly:
     *
     * <pre>
     * public Result search(Query query, Execution execution) {
     *     SearchChain searchChain = fancyChainSelectionRoutine(query);
     *     if (searchChain != null) {
     *         return new Execution(searchChain, execution.context());
     *     else {
     *         return execution.search(query);
     *     }
     * }
     * </pre>
     *
     * @param searchChain
     *            the search chain to execute
     * @param context
     *            the execution context from which this is populated (the given
     *            context is not changed nor retained by this), or null to not
     *            populate from a context
     * @throws IllegalArgumentException
     *             if searchChain is null
     */
    public Execution(Chain<? extends Searcher> searchChain, Context context) {
        this(searchChain, context, 0);
    }

    /** Creates an execution from a single searcher */
    public Execution(Searcher searcher, Context context) {
        this(new Chain<>(searcher), context, 0);
    }

    /**
     * Creates a new execution for a search chain or a single searcher. private
     * to ensure only searchChain or searcher is null (and because it's long and
     * cumbersome).
     *
     * @param searchChain the search chain to execute, must be null if searcher is set
     * @param context execution context for the search
     * @param searcherIndex index of the first searcher to invoke, see Execution(Execution)
     * @throws IllegalArgumentException if searchChain is null
     */
    @SuppressWarnings("unchecked")
    private Execution(Chain<? extends Processor> searchChain, Context context, int searcherIndex) {
        // Create a new Execution which is placed in the context of the execution of the given Context if any
        // "if any" because a context may, or may not, belong to an execution.
        // This is decided at the creation time of the Context - Context instances which do not belong
        // to an execution plays the role of data carriers between executions.
        super(searchChain, searcherIndex, context.createChildTrace(), context.createChildEnvironment());
        this.context.fill(context);
        contextCache = new Context[searchChain.components().size()];
        entryIndex = searcherIndex;
        timer = new TimeTracker(searchChain, searcherIndex);
    }

    /** Does return search(((Query)request) */
    @Override
    public final Response process(Request request) {
        return search((Query)request);
    }

    /** Calls search on the next searcher in this chain. If there is no next, an empty result is returned. */
    public Result search(Query query) {
        timer.sampleSearch(nextIndex(), context.getDetailedDiagnostics());

        // Transfer state between query and execution as the execution constructors does not do that completely
        query.getModel().setExecution(this);
        trace().setTraceLevel(query.getTrace().getLevel());

        return (Result)super.process(query);
    }

    @Override
    protected void onInvoking(Request request, Processor processor) {
        super.onInvoking(request,processor);
        final int traceDependencies = 6;
        Query query = (Query) request;
        if (query.getTrace().getLevel() >= traceDependencies) {
            query.trace(processor.getId() + " " + processor.getDependencies(), traceDependencies);
        }
    }

    /**
     * The default response returned from this kind of execution when there are not further processors
     * - an empty Result
     */
    @Override
    protected Response defaultResponse(Request request) {
        return new Result((Query)request);
    }

    /**
     * Fill hit properties with values from some in-memory attributes.
     * Not all attributes are included, and *which* attributes are
     * subject to change depending on what Vespa needs internally.
     *
     * Applications should prefer to define their own summary class
     * with only the in-memory attributes they need, and call
     * fill(result, "foo") with the name of their own summary class
     * instead of "foo".
     *
     * @deprecated use fill(Result, String)
     * 
     * @param result the result to fill
     */
    @Deprecated  // TODO Remove on Vespa 9.
    public void fillAttributes(Result result) {
        fill(result, VespaBackEndSearcher.SORTABLE_ATTRIBUTES_SUMMARY_CLASS);
    }

    /**
     * Fill hit properties with data using the default summary
     * class, possibly overridden with the 'summary' request parameter.
     * <p>
     * Fill <b>must</b> be called before any property (accessed by
     * getProperty/getField) is accessed on the hit. It should be done
     * as late as possible for performance reasons.
     * <p>
     * Calling this on already filled results has no cost.
     *
     * @param result the result to fill
     */
    public void fill(Result result) {
        fill(result, result.getQuery().getPresentation().getSummary());
    }

    /** Calls fill on the next searcher in this chain. If there is no next, nothing is done. */
    public void fill(Result result, String summaryClass) {
        timer.sampleFill(nextIndex(), context.getDetailedDiagnostics());
        Searcher current = (Searcher)next(); // TODO: Allow but skip processors which are not searchers
        if (current == null) return;

        try {
            nextProcessor();
            onInvokingFill(current, summaryClass);
            current.ensureFilled(result, summaryClass, this);
        }
        finally {
            previousProcessor();
            onReturningFill(current, summaryClass);
            timer.sampleFillReturn(nextIndex(), context.getDetailedDiagnostics(), result);
        }
    }

    private void onInvokingFill(Searcher searcher, String summaryClass) {
        int traceFillAt = 5;
        if (trace().getTraceLevel() < traceFillAt) return;
        trace().trace("Invoke fill(" + summaryClass + ") on " + searcher, traceFillAt);
    }

    private void onReturningFill(Searcher searcher, String summaryClass) {
        int traceFillAt = 5;
        if (trace().getTraceLevel() < traceFillAt) return;
        trace().trace("Return fill(" + summaryClass + ") on " + searcher, traceFillAt);
    }

    /** Calls ping on the next search in this chain. If there is no next, a Pong is created and returned. */
    public Pong ping(Ping ping) {
        // return this reference, not directly. It's needed for adding time data
        Pong annotationReference = null;

        timer.samplePing(nextIndex(), context.getDetailedDiagnostics());
        Searcher next = (Searcher)next(); // TODO: Allow but skip processors which are not searchers
        if (next == null) {
            annotationReference = new Pong();
            return annotationReference;
        }

        try {
            nextProcessor();
            annotationReference = invokePing(ping, next);
            return annotationReference;
        }
        finally {
            previousProcessor();
            timer.samplePingReturn(nextIndex(), context.getDetailedDiagnostics(), annotationReference);
        }
    }

    @Override
    protected void onReturning(Request request, Processor processor,Response response) {
        super.onReturning(request, processor, response);
        timer.sampleSearchReturn(nextIndex(), context.getDetailedDiagnostics(), (Result)response);
    }

    @Override
    protected void previousProcessor() {
        super.previousProcessor();
        popContext();
    }

    @Override
    protected void nextProcessor() {
        pushContext();
        super.nextProcessor();
    }

    private void popContext() {
        context.fill(contextCache[nextIndex()]);
        contextCache[nextIndex()] = null;
    }

    private void pushContext() {
        final Context contextToPush;
        // Do note: Never put this.context in the cache. It would be totally
        // meaningless, since it's a final.
        if (nextIndex() == entryIndex) {
            contextToPush = context.shallowCopy();
        } else {
            contextToPush = context.copyIfChanged(contextCache[nextIndex() - 1]);
        }
        contextCache[nextIndex()] = contextToPush;
    }

    private Pong invokePing(Ping ping, Searcher next) {
        Pong annotationReference;
        if (next instanceof PingableSearcher) {
            annotationReference = ((PingableSearcher) next).ping(ping, this);
        } else {
            annotationReference = ping(ping);
        }
        return annotationReference;
    }

    /**
     * Returns the search chain registry to use with this execution. This is a
     * snapshot taken at creation of this execution if available.
     */
    public SearchChainRegistry searchChainRegistry() {
        return context.searchChainRegistry();
    }

    /**
     * Returns the context of this execution, which contains various objects
     * which are looked up through a memory barrier at the point this is created
     * and which is guaranteed to be frozen during the execution of this query.
     * <p>
     * Note that the context itself can be changed. Such changes will be visible
     * to downstream searchers, but not after returning from the modifying
     * searcher. In other words, a change in the context will not be visible to
     * the preceding searchers when the result is returned from the searcher
     * which modified the context.
     */
    public Context context() {
        return context;
    }

    /**
     * @return the TimeTracker instance associated with this Execution
     */
    public TimeTracker timer() {
        return timer;
    }

}
