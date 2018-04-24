// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.rendering;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.log.LogLevel;
import com.yahoo.processing.Request;
import com.yahoo.processing.Response;
import com.yahoo.processing.execution.Execution;
import com.yahoo.processing.response.AbstractDataList;
import com.yahoo.processing.response.Data;
import com.yahoo.processing.response.DataList;
import com.yahoo.processing.response.Ordered;
import com.yahoo.processing.response.Streamed;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper class to implement processing API Response renderers. This renderer
 * will walk the data tree and call the appropriate render methods as it
 * progresses. Nodes with the same parent branch will be rendered in the order
 * in which the data is ready for consumption.
 *
 * <p>
 * This API assumes all data should be rendered. Choosing which data should be
 * rendered is the responsibility of the processing chains.
 * </p>
 *
 * @author Steinar Knutsen
 * @author Einar M R Rosenvinge
 * @author bratseth
 */
public abstract class AsynchronousSectionedRenderer<RESPONSE extends Response> extends AsynchronousRenderer<RESPONSE> {

    /**
     * Invoked once at the beginning of rendering a response. This assigns the
     * stream to be used throughput the rendering. Subsequent calls must use the
     * same stream.
     *
     * @param stream
     *            the stream to render to in this and all subsequent calls.
     * @throws IOException
     *             passed on from the stream
     */
    public abstract void beginResponse(OutputStream stream) throws IOException;

    /**
     * Invoked at the beginning of each data list, including the implicit,
     * outermost one in the response.
     *
     * @throws IOException passed on from the stream
     * @param list the data list which now will be rendered
     */
    public abstract void beginList(DataList<?> list) throws IOException;

    /**
     * Invoked for each leaf node in the data tree
     *
     * @param data the leaf node to render
     * @throws IOException passed on from the stream
     */
    public abstract void data(Data data) throws IOException;

    /**
     * Invoked at the end of each data list, including the implicit, outermost
     * one in the response.
     *
     * @param list the data list which now has no more data items to render
     * @throws IOException passed on from the stream
     */
    public abstract void endList(DataList<?> list) throws IOException;

    /**
     * Invoked once at the end of rendering a response.
     *
     * @throws IOException passed on from the stream
     */
    public abstract void endResponse() throws IOException;

    private static final Logger logger = Logger.getLogger(AsynchronousSectionedRenderer.class.getName());

    // NOTE: Renderers are *prototype objects* - a new instance is created for each rendering by invoking 
    // clone(), init() and then render().
    // Hence any field which is not reinitialized in init() or render() will be *reused* in all rendering operations
    // across all threads!

    /** The stack of listeners to ancestor datalist completions above the current one */
    private Deque<DataListListener> dataListListenerStack;

    private boolean beforeHandoverMode;
    private OutputStream stream;
    private RESPONSE response;
    private Execution execution;
    private boolean clientClosed;

    // This MUST be created in the init() method - see comment above
    private Object singleThreaded;

    // Rendering threads should never block so use one thread per core.
    // We should complete any work we have already started so use an unbounded queue.
    // The executor SHOULD be reused across all instances having the same prototype
    private final Executor renderingExecutor;
    // The executor may either be created (and thus owned) by this, or passed by injection
    private final boolean renderingExecutorIsOwned;

    private static ThreadPoolExecutor createExecutor() {
        int threadCount = Runtime.getRuntime().availableProcessors();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(threadCount, threadCount, 1L, TimeUnit.SECONDS,
                                                             new LinkedBlockingQueue<>(),
                                                             ThreadFactoryFactory.getThreadFactory("rendering"));
        executor.prestartAllCoreThreads();
        return executor;
    }

    private SettableFuture<Boolean> success;

    private ContentChannel channel;
    private CompletionHandler completionHandler;
    private boolean networkIsInitialized;

    private boolean isInitialized;

    /**
     * Create an renderer instance not yet associated with any request
     * processing or network for easy subclassing. It is the handler's
     * responsibility to wire in the resources needed by a renderer
     * before use.
     */
    public AsynchronousSectionedRenderer() {
        this(null);
    }

    /**
     * Create an renderer using the specified executor instead of the default one which should be used for production.
     * Using a custom executor is useful for tests to avoid creating new threads for each renderer registry.
     * 
     * @param executor the executor to use or null to use the default executor suitable for production
     */
    public AsynchronousSectionedRenderer(Executor executor) {
        isInitialized = false;
        if (executor == null) {
            renderingExecutor = createExecutor();
            renderingExecutorIsOwned = true;
        }
        else {
            renderingExecutor = executor;
            renderingExecutorIsOwned = false;
        }
    }

    /**
     * <p>Render this response using the renderer's own threads and return a future indicating whether the rendering
     * was successful. The data list tree will be traversed asynchronously, and
     * the pertinent methods will be called as data becomes available.</p>
     *
     * <p>If rendering fails, the exception causing this will be wrapped in an
     * ExecutionException and thrown from blocked calls to Future.get()</p>
     *
     * @return a future indicating whether rendering was successful
     */
    @Override
    public final ListenableFuture<Boolean> render(OutputStream stream, RESPONSE response,
                                                  Execution execution, Request request) {
        if (beforeHandoverMode) { // rendering has already started or is already complete
            beforeHandoverMode = false;
            if ( ! dataListListenerStack.isEmpty() &&
                 dataListListenerStack.getFirst().list.incoming().isComplete()) {
                // We're not waiting for async completion, so kick off more rendering due to the implicit complete
                // (return Response from chain) causing this method to be called
                getExecutor().execute(dataListListenerStack.getFirst());
            }
            return success;
        }
        else { // This is the start of rendering
            return startRender(stream, response, execution, request);
        }
    }

    @Override
    public void deconstruct() {
        super.deconstruct();
        if (renderingExecutorIsOwned && renderingExecutor instanceof ThreadPoolExecutor)
            shutdown((ThreadPoolExecutor) renderingExecutor);
    }
    
    private void shutdown(ThreadPoolExecutor executor) {
        executor.shutdown();
        try {
            if ( ! executor.awaitTermination(30, TimeUnit.SECONDS))
                throw new RuntimeException("Rendering thread pool did not shutdown in 30 seconds");
        }
        catch (InterruptedException e) {
            // return
        }
    }
    
    /**
     * Initiate rendering before handover to rendering threads.
     * This is rendering which happens before the Response is returned from the main chain,
     * caused by freezing of DataLists.
     * At this point the worker thread still owns the Response, so all this rendering must happen
     * on the caller thread invoking freeze (that is, on the thread calling this).
     */
    public final ListenableFuture<Boolean> renderBeforeHandover(OutputStream stream, RESPONSE response,
                                                                Execution execution, Request request) {
        beforeHandoverMode = true;
        if (!isInitialized) throw new IllegalStateException("render() invoked before init().");

        return startRender(stream, response, execution, request);
    }

    private ListenableFuture<Boolean> startRender(OutputStream stream, RESPONSE response,
                                                  Execution execution, Request request) {
        this.response = response;
        this.stream = stream;
        this.execution = execution;
        DataListListener parentOfTopLevelListener = new DataListListener(new ParentOfTopLevel(request,response.data()), null);
        dataListListenerStack.addFirst(parentOfTopLevelListener);
        success = SettableFuture.create();
        try {
            getExecutor().execute(parentOfTopLevelListener);
        } catch (RejectedExecutionException e) {
            parentOfTopLevelListener.closeIO(e);
        }
        return success;
    }

    /**
     * Returns the executor in which to execute a listener.
     * Before handover this *must* be the calling thread, because listeners are free to modify the dataList.
     * After handover it can be any thread in the renderer pool.
     * Note that as some listeners may be set up before handover and executed after, it is possible that some rendering
     * inadvertently work ends up in async data producing threads in some cases.
     */
    Executor getExecutor() {
        return beforeHandoverMode ? MoreExecutors.directExecutor() : renderingExecutor;
    }
    /** For inspection only; use getExecutor() for execution */
    Executor getRenderingExecutor() { return renderingExecutor; }    

    /** The outermost execution which was run to create the response to render. */
    public Execution getExecution() { return execution; }

    /** The response render callbacks are generated from. */
    public Response getResponse() { return response; }

    /** Returns whether the client this is rendering to has closed the connection */
    protected boolean clientClosed() { return clientClosed; }

    /** This hook is called once when the renderer detects that the client has closed the connection */
    protected void onClientClosed() { }

    /**
     * How deep into the tree of nested data lists the callback currently is.
     * beginList() is invoked after this this is increased, and endList() is
     * invoked before it is decreased.
     *
     * @return an integer of 1 or above
     */
    public int getRecursionLevel() {
        return dataListListenerStack.size()-1;
    }

    /**
     * For internal use: Expose JDisc wiring to ensure asynchronous cleanup.
     *
     * @param channel the channel to the client receiving the response
     * @param completionHandler the JDisc completion handler which will be invoked at the end
     *        of the rendering
     * @throws IllegalStateException if attempted invoked more than once
     */
    @Override
    public final void setNetworkWiring(ContentChannel channel, CompletionHandler completionHandler) {
        if (networkIsInitialized)
            throw new IllegalStateException("Network wiring already set and can only be set once.");

        this.channel = channel;
        this.completionHandler = completionHandler;
        networkIsInitialized = true;
    }

    /**
     * Do per instance initialization. If overriding this in a subclass, not
     * invoking it in the subclass' implementation will most likely cause the
     * rendering to fail with an exception.
     */
    @Override
    public void init() {
        beforeHandoverMode = false;
        clientClosed = false;
        singleThreaded = new Object();
        dataListListenerStack = new ArrayDeque<>();
        networkIsInitialized = false;
        isInitialized  = true;
    }

    /**
     * A listener to async completion of a data list.
     * All rendering is done by callbacks to this, even in the sync case (where the callback happens immediately).
     * One such listener is registered for every data list encountered during rendering.
     * Only the last one registered is allowed to run at any point in time, as that one renders the lowest level
     * list not yet completed (rendering, of course is depth first).
     * <p>
     * A stack of registered renderers is maintained to maintain this constraint.
     * <p>
     * A renderer maintains state sufficient to allow it to resume rendering at a later stage.
     * This is to be able to render child lists to completion before completing rendering of the parent list.
     * In addition, this feature is used by DataListeners (see below).
     */
    private class DataListListener extends RendererListener {

        /** The index of the next data item where rendering should be initiated in this list */
        private int currentIndex = 0;

        /** Children of this which has started rendering but not yet completed */
        private int uncompletedChildren = 0;

        private boolean listStartIsRendered = false;

        /** The list which this is listening to */
        private final DataList list;

        /** The listener to the parent of this list, or null if this is the root */
        private final DataListListener parent;

        public DataListListener(DataList list, DataListListener parent) {
            this.list = list;
            this.parent = parent;
        }

        @Override
        protected void render() throws IOException, InterruptedException, ExecutionException {
            if (dataListListenerStack.peekFirst() != this)
                return; // This listens to some ancestor of the current list, do this later
            if (beforeHandoverMode &&  ! list.isFrozen())
                return; // Called on completion of a list which is not frozen yet - hold off until frozen

            if ( ! beforeHandoverMode)
                list.complete().get(); // trigger completion if not done already to invoke any listeners on that event
            boolean startedRendering = renderData();
            if ( ! startedRendering || uncompletedChildren > 0) return; // children must render to completion first
            if (list.complete().isDone()) // might not be when in before handover mode
                endListLevel();
            else
                stream.flush();
        }

        private void endListLevel() throws IOException {
            endRenderLevel(list);
            stream.flush();
            dataListListenerStack.removeFirst();
            if (parent != null)
                parent.childCompleted();
            list.close();
        }

        /** Called each time a direct child of this completed. */
        private void childCompleted() {
            uncompletedChildren--;

            if (uncompletedChildren > 0) return;
            if (list.incoming().isComplete()) // i) if the parent had completed earlier, render it now, see ii)
                run();
        }

        /**
         * Resumes rendering data from the current position.
         * Called both on completion (by this), and when new data is available (from the new data listener).
         *
         * @return whether this started rendering
         */
        @SuppressWarnings("unchecked")
        private boolean renderData() throws IOException {
            if (dataListListenerStack.peekFirst() != this) return false; // This listens to some ancestor of the current list, do this later
            renderDataListStart();

            // Add newly arrived data, and as a consequence run data listeners
            for (Object data : list.incoming().drain())
                list.add((Data) data);

            renderDataList(list);
            return true;
        }

        void renderDataListStart() throws IOException {
            if ( ! listStartIsRendered) {
                if (list instanceof ParentOfTopLevel)
                    beginResponse(stream);
                else
                    beginList(list);
                listStartIsRendered = true;
            }
        }

        /**
         * Renders a list
         */
        private void renderDataList(DataList list) throws IOException {
            final boolean ordered = isOrdered(list);
            while (currentIndex < list.asList().size()) {
                Data data = list.get(currentIndex++);
                if (data instanceof DataList) {
                    listenTo((DataList)data, ordered && isStreamed((DataList)data));
                    uncompletedChildren++;
                    if (ordered)
                        return; // ii) Resumed by the child list when done, see i)
                }
                else {
                    data(data);
                }
            }
        }

        private void listenTo(DataList subList, boolean listenToNewDataAdded) throws IOException {
            DataListListener listListener = new DataListListener(subList,this);
            dataListListenerStack.addFirst(listListener);

            if (listenToNewDataAdded)
                subList.incoming().addNewDataListener(new DataListener(listListener), getExecutor());

            flushIfLikelyToSuspend(subList);

            subList.addFreezeListener(listListener, getExecutor());
            subList.complete().addListener(listListener, getExecutor());
            subList.incoming().completed().addListener(listListener, getExecutor());
        }

        private boolean isOrdered(DataList dataList) {
            if (! (dataList instanceof Ordered))
                return true; // all lists are ordered by default
            return ((Ordered)dataList).isOrdered();
        }

        private boolean isStreamed(DataList dataList) {
            if (! (dataList instanceof Streamed))
                return true; // all lists are streamed by default
            return ((Streamed)dataList).isStreamed();
        }

        private void endRenderLevel(DataList<?> current) throws IOException {
            if (current instanceof ParentOfTopLevel) {
                endResponse();
                closeIO(null);
            }
            else {
                endList(current);
            }
        }

        private void closeIO(Exception failed) {
            IOException closeException = null;

            try {
                stream.close();
            } catch (IOException e) {
                closeException = e;
                logger.log(LogLevel.WARNING, "Exception caught while closing stream to client.", e);
            } finally {
                if (failed != null) {
                    success.setException(failed);
                } else if (closeException != null) {
                    success.setException(closeException);
                } else {
                    success.set(true);
                }
                if (channel != null) {
                    channel.close(completionHandler);
                }
            }
        }

        @Override
        public String toString() {
            return "listener to " + list;
        }

    }

    /**
     * A data listener is invoked every time new data is available in an incoming list, such that this data
     * can be rendered before completion of the entire list (streaming).
     * <p>
     * One data renderer is registered for every incoming data list.
     * It will delegate to the data list listener of the same list such that the correct rendering progress state is
     * shared between rendering here and from the completion listener.
     */
    private class DataListener extends RendererListener {

        /** The listener to completion of the data list this listens to new data in. */
        private DataListListener dataListListener;

        public DataListener(DataListListener dataListListener) {
            this.dataListListener = dataListListener;
        }

        protected void render() throws IOException {
            dataListListener.renderData();
            flushIfLikelyToSuspend(dataListListener.list);
        }

    }

    private abstract class RendererListener implements Runnable {

        protected abstract void render() throws IOException, InterruptedException, ExecutionException;

        public void run() {
            try {
                synchronized (singleThreaded) {
                    try {
                        render();
                    } catch (Exception e) {
                        Level level = LogLevel.WARNING;
                        if ((e instanceof IOException)) {
                            level = LogLevel.DEBUG;
                            if ( ! clientClosed) {
                                clientClosed = true;
                                onClientClosed();
                            }
                        }
                        if (logger.isLoggable(level)) {
                            logger.log(level, "Exception caught during response rendering.", e);
                        }
                        if (channel != null) {
                            try {
                                channel.close(completionHandler);
                            } catch (Exception ignored) {
                            }
                        }
                        success.setException(e);
                    }
                }
            } catch (Error e) {
                // We are in free-range thread land, and a hanging container is really no fun at all.
                com.yahoo.protect.Process.logAndDie("Caught fatal error during rendering.", e);
            }
        }

        protected void flushIfLikelyToSuspend(DataList list) throws IOException {
            // If the listener is not complete, we will (likely) suspend rendering
            if ( ! list.incoming().isComplete()) stream.flush();
        }

    }

    /**
     * This must be pushed on the stack first to get things started off, given that the stack is expected to
     * contain the parent of each element (including the topmost)
     */
    private static class ParentOfTopLevel extends AbstractDataList {

        private DataList trueTopLevel;

        public ParentOfTopLevel(Request request,DataList trueTopLevel) {
            super(request);
            this.trueTopLevel = trueTopLevel;
            freeze();
        }

        @Override
        public Data add(Data data) {
            throw new IllegalStateException("We're not supposed to add to this");
        }

        @Override
        public void addDataListener(Runnable listener) {
            throw new IllegalStateException("We're not supposed to listen to or add to this");
        }

        @Override
        public Data get(int index) {
            if (index>0) throw new IndexOutOfBoundsException();
            return trueTopLevel;
        }

        @Override
        public List<Data> asList() {
            return Collections.<Data>singletonList(trueTopLevel);
        }

        @Override
        public String toString() {
            return "ParentOfTopLevel";
        }

    }

}
