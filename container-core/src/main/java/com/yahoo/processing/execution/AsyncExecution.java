// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.execution;

import com.yahoo.component.chain.Chain;
import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.processing.Processor;
import com.yahoo.processing.Request;
import com.yahoo.processing.Response;
import com.yahoo.processing.request.ErrorMessage;
import com.yahoo.processing.response.FutureResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

/**
 * <p>Provides asynchronous execution of processing chains. Usage:</p>
 *
 * <pre>
 * Execution execution = new Execution(chain);
 * AsyncExecution asyncExecution = new AsyncExecution(execution);
 * Future&lt;Response&gt; future = asyncExecution.process(request)
 * try {
 *     result = future.get(timeout, TimeUnit.milliseconds);
 * } catch(TimeoutException e) {
 *     // Handle timeout
 * }
 * </pre>
 *
 * <p>
 * The request is not thread safe. A clone() must be made for each parallel processing.
 * </p>
 *
 * @author bratseth
 * @see Execution
 */
public class AsyncExecution {

    private static final ThreadFactory threadFactory = ThreadFactoryFactory.getThreadFactory("processing");

    private static final Executor executorMain = createExecutor();
    private static final Executor createExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(100,
                Integer.MAX_VALUE, 1L, TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>(false), threadFactory);
        // Prestart needed, if not all threads will be created by the fist N tasks and hence they might also
        // get the dreaded thread locals initialized even if they will never run.
        // That counters what we we want to achieve with the Q that will prefer thread locality.
        executor.prestartAllCoreThreads();
        return executor;
    }

    /**
     * The execution of this
     */
    private final Execution execution;

    /**
     * Create an async execution of a single processor
     */
    public AsyncExecution(Processor processor, Execution parent) {
        this(new Execution(processor, parent));
    }

    /**
     * Create an async execution of a chain
     */
    public AsyncExecution(Chain<? extends Processor> chain, Execution parent) {
        this(new Execution(chain, parent));
    }

    /**
     * Creates an async execution from an existing execution. This async
     * execution will execute the chain from the given execution, starting
     * from the next processor in that chain. This is handy to execute
     * multiple executions to the rest of the chain in parallel.
     * <p>
     * The state of the given execution is read on construction of this and not
     * used later - the argument execution can be reused for other purposes.
     *
     * @param execution the execution from which the state of this is created
     */
    public AsyncExecution(Execution execution) {
        this.execution = new Execution(execution);
    }

    /**
     * Performs an async processing. Note that the given request cannot be simultaneously
     * used in multiple such processings - a clone must be created for each.
     */
    public FutureResponse process(Request request) {
        return getFutureResponse(new Callable<Response>() {
            @Override
            public Response call() {
                return execution.process(request);
            }
        }, request);
    }

    private static <T> Future<T> getFuture(final Callable<T> callable) {
        FutureTask<T> future = new FutureTask<>(callable);
        executorMain.execute(future);
        return future;
    }

    private FutureResponse getFutureResponse(Callable<Response> callable, Request request) {
        FutureResponse future = new FutureResponse(callable, execution, request);
        executorMain.execute(future.delegate());
        return future;
    }

    /*
     * Waits for all futures until the given timeout. If a FutureResult isn't
     * done when the timeout expires, it will be cancelled, and it will return a
     * response. All unfinished Futures will be cancelled.
     *
     * @return the list of responses in the same order as returned from the task collection
     */
    // Note that this may also be achieved using guava Futures. Not sure if this should be deprecated because of it.
    public static List<Response> waitForAll(Collection<FutureResponse> tasks, long timeout) {

        // Copy the list in case it is modified while we are waiting
        List<FutureResponse> workingTasks = new ArrayList<>(tasks);
        @SuppressWarnings({"rawtypes", "unchecked"})
        Future task = getFuture(new Callable() {
            @Override
            public List<Future> call() {
                for (FutureResponse task : workingTasks) {
                    task.get();
                }
                return null;
            }
        });

        try {
            task.get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | InterruptedException | ExecutionException e) {
            // Handle timeouts below
        }

        List<Response> responses = new ArrayList<>(tasks.size());
        for (FutureResponse future : workingTasks)
            responses.add(getTaskResponse(future));
        return responses;
    }

    private static Response getTaskResponse(FutureResponse future) {
        if (future.isDone() && !future.isCancelled()) {
            return future.get(); // Since isDone() = true, this won't block.
        } else { // Not done and no errors thrown
            return new Response(future.getRequest(), new ErrorMessage("Timed out waiting for " + future));
        }
    }

}
