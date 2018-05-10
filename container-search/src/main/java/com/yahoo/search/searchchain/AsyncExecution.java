// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchchain;

import com.yahoo.component.chain.Chain;
import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

/**
 * Provides asynchronous execution of searchchains.
 *
 * <p>
 * AsyncExecution is implemented as an asynchronous wrapper around Execution
 * that returns Future.
 * </p>
 *
 * This is used in the following way
 *
 * <pre>
 * Execution execution = new Execution(searchChain, context);
 * AsyncExecution asyncExecution = new AsyncExecution(execution);
 * Future&lt;Result&gt; future = asyncExecution.search(query)
 * try {
 *     result = future.get(timeout, TimeUnit.milliseconds);
 * } catch(TimeoutException e) {
 *     // Handle timeout
 * }
 * </pre>
 *
 * <p>
 * Note that the query is not a thread safe object and cannot be shared between
 * multiple concurrent executions - a clone() must be made, or a new query
 * created for each AsyncExecution instance.
 * </p>
 *
 * @see com.yahoo.search.searchchain.Execution
 * @author Arne Bergene Fossaa
 */
public class AsyncExecution {

    private static final ThreadFactory threadFactory = ThreadFactoryFactory.getThreadFactory("search");

    private static final Executor executorMain = createExecutor();

    private static Executor createExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(100, Integer.MAX_VALUE, 1L, TimeUnit.SECONDS,
                                                            new SynchronousQueue<>(false), threadFactory);
        // Prestart needed, if not all threads will be created by the fist N tasks and hence they might also
        // get the dreaded thread locals initialized even if they will never run.
        // That counters what we we want to achieve with the Q that will prefer thread locality.
        executor.prestartAllCoreThreads();
        return executor;
    }

    /** The execution this executes */
    private final Execution execution;

    /**
     * Creates an async execution.
     *
     * @param chain the chain to execute
     * @param execution the execution holding the context of this
     */
    public AsyncExecution(Chain<? extends Searcher> chain, Execution execution) {
        this(execution.context(), chain);
    }

    /**
     * Creates an async execution.
     *
     * @param chain the chain to execute
     * @param context the the context of this
     */
    public AsyncExecution(Chain<? extends Searcher> chain, Execution.Context context) {
        this(context, chain);
    }

    /**
     * <p>
     * Creates an async execution from an existing execution. This async
     * execution will execute the chain from the given execution, <i>starting
     * from the next searcher in that chain.</i> This is handy to execute
     * multiple queries to the rest of the chain in parallel. If the Execution
     * is freshly instantiated, the search will obviously start from the first
     * searcher.
     * </p>
     *
     * <p>
     * The state of the given execution is read on construction of this and not
     * used later - the argument execution can be reused for other purposes.
     * </p>
     *
     * @param execution the execution from which the state of this is created
     *
     * @see Execution#Execution(Chain, com.yahoo.search.searchchain.Execution.Context)
     * @see #AsyncExecution(Chain, Execution)
     */
    public AsyncExecution(Execution execution) {
        this.execution = new Execution(execution);
    }

    private AsyncExecution(Execution.Context context, Chain<? extends Searcher> chain) {
        this.execution = new Execution(chain, context);
    }

    /**
     * Does an async search, note that the query argument cannot simultaneously
     * be used to execute any other searches, a clone() must be made of the
     * query for each async execution if the same query is to be used in more
     * than one.
     *
     * @see com.yahoo.search.searchchain.Execution
     */
    public FutureResult search(Query query) {
        return getFutureResult(() -> execution.search(query), query);
    }

    public FutureResult searchAndFill(Query query) {
        return getFutureResult(() -> {
            Result result = execution.search(query);
            execution.fill(result, query.getPresentation().getSummary());
            return result;
        }, query);
    }

    private static Executor getExecutor() {
        return executorMain;
    }

    /**
     * The future of this functions returns the original Result
     *
     * @see com.yahoo.search.searchchain.Execution
     */
    public FutureResult fill(Result result, String summaryClass) {
        return getFutureResult(() -> {
            execution.fill(result, summaryClass);
            return result;
        }, result.getQuery());

    }

    private static <T> Future<T> getFuture(Callable<T> callable) {
        final FutureTask<T> future = new FutureTask<>(callable);
        getExecutor().execute(future);
        return future;
    }

    private static Future<Void> runTask(Runnable runnable) {
        return getFuture(() -> {
            runnable.run();
            return null;
        });
    }

    private FutureResult getFutureResult(Callable<Result> callable, Query query) {
        FutureResult future = new FutureResult(callable, execution, query);
        getExecutor().execute(future);
        return future;
    }

    /*
     * Waits for all futures until the given timeout. If a FutureResult isn't
     * done when the timeout expires, it will be cancelled, and it will return a
     * result. All unfinished Futures will be cancelled.
     *
     * @return the list of results in the same order as returned from the task
     * collection
     */
    public static List<Result> waitForAll(Collection<FutureResult> tasks, long timeoutMs) {
        // Copy the list in case it is modified while we are waiting
        List<FutureResult> workingTasks = new ArrayList<>(tasks);
        try {
            runTask(() -> {
                for (FutureResult task : workingTasks)
                    task.get();
            }).get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | InterruptedException | ExecutionException e) {
            // Handle timeouts below
        }

        List<Result> results = new ArrayList<>(tasks.size());
        for (FutureResult atask : workingTasks) {
            Result result;
            if (atask.isDone() && !atask.isCancelled()) {
                result = atask.get(); // Since isDone() = true, this won't block.
            } else { // Not done and no errors thrown
                result = new Result(atask.getQuery(), atask.createTimeoutError());
            }
            results.add(result);
        }
        return results;
    }

}
