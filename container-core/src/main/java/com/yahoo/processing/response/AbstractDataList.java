// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.response;

import com.yahoo.component.provider.ListenableFreezableClass;
import com.yahoo.processing.Request;
import com.yahoo.processing.impl.ProcessingFuture;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A convenience superclass for dataList implementations which handles references to the request and to incoming data.
 *
 * @author bratseth
 */
public abstract class AbstractDataList<DATATYPE extends Data> extends ListenableFreezableClass implements DataList<DATATYPE>, Streamed, Ordered  {

    private final boolean ordered;
    private final boolean streamed;

    /**
     * The request which caused this to be created
     */
    private final Request request;

    /**
     * The recipient of incoming data to this. Never null, but may be a null recipient.
     */
    private final IncomingData<DATATYPE> incomingData;

    private final CompletableFuture<DataList<DATATYPE>> completedFuture;

    /**
     * Creates a simple data list which does not allow late incoming data
     *
     * @param request the request which created this data list
     */
    protected AbstractDataList(Request request) {
        // Cannot call the constructor below because this must be given out below
        this.request = request;
        this.incomingData = new IncomingData.NullIncomingData<>(this);
        this.completedFuture = new DrainOnGetFuture<>(this);
        ordered = true;
        streamed = true;
    }

    /**
     * Creates a simple data list which receives incoming data in the given instance
     *
     * @param request      the request which created this data list, never null
     * @param incomingData the recipient of incoming data to this list, never null
     */
    protected AbstractDataList(Request request, IncomingData<DATATYPE> incomingData) {
        this(request, incomingData, true, true);
    }

    /**
     * Creates a simple data list which receives incoming data in the given instance
     *
     * @param request      the request which created this data list, never null
     * @param incomingData the recipient of incoming data to this list, never null
     */
    protected AbstractDataList(Request request, IncomingData<DATATYPE> incomingData, boolean ordered, boolean streamed) {
        if (request == null) throw new NullPointerException("Request cannot be null");
        if (incomingData == null) throw new NullPointerException("incomingData cannot be null");

        this.request = request;
        this.incomingData = incomingData;
        this.completedFuture = new DrainOnGetFuture<>(this);
        this.ordered = ordered;
        this.streamed = streamed;
    }

    /**
     * Returns the request which created this data
     */
    public Request request() {
        return request;
    }

    /**
     * Returns the holder of incoming data to this.
     * This may be used to add, consume, wait for and be notified about incoming data.
     * If this instance does not support late incoming data, the read methods of the return object behaves
     * as expected and is synchronization free. The write methods throws an exception.
     */
    public IncomingData<DATATYPE> incoming() {
        return incomingData;
    }

    @Override public CompletableFuture<DataList<DATATYPE>> completeFuture() { return completedFuture; }

    @Override
    public boolean isOrdered() { return ordered; }

    @Override
    public boolean isStreamed() { return streamed; }

    public String toString() {
        return super.toString() + (completeFuture().isDone() ? " [completed]" : " [incomplete, " + incoming() + "]");
    }

    public static final class DrainOnGetFuture<DATATYPE extends Data> extends ProcessingFuture<DataList<DATATYPE>> {

        private final DataList<DATATYPE> owner;

        public DrainOnGetFuture(DataList<DATATYPE> owner) {
            this.owner = owner;
        }

        /**
         * Returns false as this is not cancellable
         */
        @Override
        public boolean cancel(boolean b) {
            return false;
        }

        /**
         * Returns false as this is not cancellable
         */
        @Override
        public boolean isCancelled() {
            return false;
        }

        /**
         * Wait until all data is available. When this returns all data is available in the returned data list.
         */
        @Override
        public DataList<DATATYPE> get() throws InterruptedException, ExecutionException {
            return drain(owner.incoming().completedFuture().get());
        }

        /**
         * Wait until all data is available.
         * When and if this returns normally all data is available in the returned data list
         */
        @Override
        public DataList<DATATYPE> get(long timeout, TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException {
            return drain(owner.incoming().completedFuture().get(timeout, timeUnit));
        }

        private DataList<DATATYPE> drain(DataList<DATATYPE> dataList) {
            for (DATATYPE item : dataList.incoming().drain())
                dataList.add(item);
            complete(dataList); // Signal completion to listeners
            return dataList;
        }

    }

}
