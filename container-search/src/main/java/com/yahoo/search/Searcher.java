// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search;

import com.yahoo.component.ComponentId;
import com.yahoo.processing.Processor;
import com.yahoo.processing.Response;
import com.yahoo.search.searchchain.Execution;

import java.util.logging.Logger;

/**
 * Superclass of all {@link com.yahoo.component.Component Components} which produces Results in response to
 * Queries by calling the {@link #search search} method.
 * <p>
 * Searchers are participants in <i>chain of responsibility</i> {@link com.yahoo.search.searchchain.SearchChain search chains}
 * where they passes the Queries downwards by synchroneously calling the next Searcher in the chain, and returns the
 * Results back up as the response.
 * <p>
 * Any Searcher may
 * <ul>
 * <li>Do modifications to the Query before passing it on (a <i>query rerwiter</i>)
 * <li>Do modifications to the Result before passing it on up, e.g removing altering, reorganizing or adding Hits
 * (a <i>result processor</i>)
 * <li>Pass the Query on to multiple other search chains, either in series
 * (by creating a new {@link com.yahoo.search.searchchain.Execution} for each chain), or in parallel (by creating a
 * {@link com.yahoo.search.searchchain.AsyncExecution}) (a <i>federator</i>)
 * <li>Create a Result and pass it back up, either by calling some other node(s) to get the data, or by creating the
 * Result from internal data (a <i>source</i>)
 * <li>Pass some query on downwards multiple times, or in different ways, typically each time depending of the Result
 * returned the last time (a <i>workflow</i>)
 * </ul>
 *
 * <p>...or some combination of the above of course. Note that as Searchers work synchronously, any information can be
 * retained on the stack in the Searcher from the Query is received until the Result is returned simply by declaring
 * variables for the data in the search method (or whatever it calls), and for the same reason workflows are
 * implemented as Java code. However, searchers are executed by many threads, for different Queries, in parallell, so
 * any mutable data shared between queries (and hence stored as instance members must be accessed multithread safely.
 * In many cases, shared data can simply be instantiated in the constructor and used in read-only mode afterwards
 * <p>
 * <b>Searcher lifecycle:</b> A searcher has a simple life-cycle:
 *
 * <ul>
 * <li><b>Construction: While a constructor is running.</b> A searcher is handed its id and configuration
 * (if any) in the constructor. During construction, the searcher should build any in-memory structures needed.
 * A new instance of the searcher will be created when the configuration is changed.
 * Constructors are called with this priority:
 *
 *   <ul>
 *     <li>The constructor taking a ComponentId, followed by the highest number of config classes (subclasses of
 *         {@link com.yahoo.config.ConfigInstance}) as arguments.
 *     <li>The constructor taking a string id followed by the highest number of config classes as arguments.
 *     <li>The constructor taking only the highest number of config classes as arguments.
 *     <li>The constructor taking a ComponentId as the only argument
 *     <li>The constructor taking a string id as the only argument
 *     <li>The default (no-argument) constructor.
 *   </ul>
 *
 * If none of these constructors are declared, searcher construction will fail.
 *
 * <li><b>In service: After the constructor has returned.</b> In this phase, searcher service methods are
 * called at any time by multiple threads in parallel.
 * Implementations should avoid synchronization and access to volatiles as much as possible by keeping
 * data structures build in construction read-only.
 *
 * <li><b>Deconstruction: While deconstruct is running.</b> All Searcher service method calls have completed when
 * this method is called. When it returns, the searcher will be eligible for garbage collection.
 *
 * </ul>
 *
 * @author bratseth
 */
public abstract class Searcher extends Processor {

    // Note to developers: If you think you should add something here you are probably wrong
    //                     Create a subclass containing the new method instead.
    private final Logger logger = Logger.getLogger(getClass().getName());

    public Searcher() {}

    /** Creates a searcher from an id */
    public Searcher(ComponentId id) {
        super();
        initId(id);
    }

    /**
     * Override this to implement your searcher.
     * <p>
     * Searcher implementation subclasses will, depending on their type of logic, do one of the following:
     * <ul>
     * <li><b>Query processors:</b> Access the query, then call execution.search and return the result
     * <li><b>Result processors:</b> Call execution.search to get the result, access it and return
     * <li><b>Sources</b> (which produces results): Create a result, add the desired hits and return it.
     * <li><b>Federators</b> (which forwards the search to multiple subchains): Call search on the
     * desired subchains in parallel and get the results. Combine the results to one and return it.
     * <li><b>Workflows:</b> Call execution.search as many times as desired, using different queries.
     * Eventually return a result.
     * </ul>
     * <p>
     * Hits come in two kinds - <i>concrete hits</i> are actual
     * content of the kind requested by the user, <i>meta hits</i> are
     * hits which provides information about the collection of hits,
     * on the query, the service and so on.
     * <p>
     * The query specifies a window into a larger result list that must be returned from the searcher
     * through <i>hits</i> and <i>offset</i>;
     * Searchers which returns list of hits in the top level in the result
     * must return at least <i>hits</i> number of hits (or if impossible; all that are available),
     * starting at the given offset.
     * In addition, searchers are allowed to return
     * any number of meta hits (although this number is expected to be low).
     * For hits contained in nested hit groups, the concept of a window defined by hits and offset
     * is not well defined and does not apply.
     * <p>
     * Error handling in searchers:
     * <ul>
     * <li>Unexpected events: Throw any RuntimeException. This query will fail
     * with the exception message, and the error will be logged
     * <li>Expected events: Create (new Result(Query, ErrorMessage) or add
     * result.setErrorIfNoOtherErrors(ErrorMessage) an error message to the Result.
     * <li>Recoverable user errors: Add a FeedbackHit explaining the condition
     * and how to correct it.
     * </ul>
     *
     * @param query the query
     * @return the result of making this query
     */
    public abstract Result search(Query query,Execution execution);

    /** Use the search method in Searcher processors. This forwards to it. */
    @Override
    public final Response process(com.yahoo.processing.Request request, com.yahoo.processing.execution.Execution execution) {
        return search((Query)request, (Execution)execution);
    }

    /**
     * Fill hit properties with data using the given summary class.
     * Calling this on already filled results has no cost.
     * <p>
     * This needs to be overridden by <i>federating</i> searchers to contact search sources again by
     * propagating the fill call down through the search chain, and by <i>source</i> searchers
     * which talks to fill capable backends to request the data to be filled. Other searchers do
     * not need to override this.
     *
     * @param result the result to fill
     * @param summaryClass the name of the collection of fields to fetch the values of, or null to use the default
     */
    public void fill(Result result, String summaryClass, Execution execution) {
        execution.fill(result, summaryClass);
    }

    /**
     * Fills the result if it is not already filled for the given summary class.
     * See the fill method.
     */
    public final void ensureFilled(Result result, String summaryClass, Execution execution) {
        if (summaryClass == null)
            summaryClass = result.getQuery().getPresentation().getSummary();

        if ( ! result.isFilled(summaryClass)) {
            fill(result, summaryClass, execution);
        }
        else {
            int fillRejectTraceAt = 3;
            if (result.getQuery().getTraceLevel() >= fillRejectTraceAt)
                result.getQuery().trace("Ignoring fill(" + summaryClass + "): " +
                                        ( result.hits().getFilled() == null ? "Hits are unfillable" : "Hits already filled" ) +
                                        ": result.hits().getFilled()=" + result.hits().getFilled(), fillRejectTraceAt);
        }
    }

    /** Returns a logger unique for the instance subclass */
    protected Logger getLogger() { return logger; }

    /** Returns "searcher 'getId()'" */
    @Override
    public String toString() {
        return "searcher '" + getIdString() + "'";

    }

}
