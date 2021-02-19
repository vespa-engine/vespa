// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing;

import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.yahoo.component.provider.ListenableFreezableClass;
import com.yahoo.concurrent.SystemTimer;
import com.yahoo.processing.execution.ResponseReceiver;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.processing.request.ErrorMessage;
import com.yahoo.processing.response.ArrayDataList;
import com.yahoo.processing.response.Data;
import com.yahoo.processing.response.DataList;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A Response to a Request.
 * <p>
 * A Response contains a list of Data items, which may (through Data implementations) contain payload data and/or
 * further nested data lists.
 * <p>
 * Frameworks built on top of processing may subclass this to create a stricter definition of a response.
 * Processors producing Responses should not create subclasses but should instead
 * create additional instances/subclasses of Data. Such Processors should always create Response instances by calling
 * execution.process(request), which will return an empty Response if there are no further processors in the chain.
 * <p>
 * Do not cache this as it may hold references to objects that should be garbage collected.
 *
 * @author bratseth
 */
public class Response extends ListenableFreezableClass {

    private final static CompoundName freezeListenerKey =new CompoundName("processing.freezeListener");

    private final DataList<?> data;

    /** Creates a request containing an empty array data list */
    public Response(Request request) {
        this(ArrayDataList.create(request));
    }

    /** Creates a response containing a list of data */
    public Response(DataList<?> data) {
        this.data = data;

        Runnable freezeListener = null;
        Request request = data.request();
        if (request != null) // subclasses of DataList may not ensure this
            freezeListener = (Runnable)request.properties().get(freezeListenerKey);
        if (freezeListener != null) {
            if (freezeListener instanceof ResponseReceiver)
                ((ResponseReceiver)freezeListener).setResponse(this);
            data.addFreezeListener(freezeListener, MoreExecutors.directExecutor());
        }
    }

    /**
     * Convenience constructor which adds the given error message to the given request
     */
    public Response(Request request, ErrorMessage errorMessage) {
        this(ArrayDataList.create(request));
        request.errors().add(errorMessage);
    }

    /**
     * Processors which merges another request into this must call this method to notify the response.
     * This does not modify the data of either response.
     */
    public void mergeWith(Response other) {
    }

    /**
     * Returns the top level list of data items of this response
     */
    public DataList data() {
        return data;
    }

    // ------ static utilities ----------------------------------------------------------------------------

    /**
     * Returns a future in which the given data list and all lists nested within it are completed.
     * The only use of the returned future is to call a get() method on it to complete the given dataList and
     * all dataLists nested below it recursively.
     * <p>
     * Lists are completed in prefix, depth-first order. DataLists added after the point when this method is called
     * will not be completed.
     *
     * @param rootDataList the list to complete recursively
     * @return the future in which all data in and below this list is complete, as the given root dataList for convenience
     */
    public static <D extends Data> ListenableFuture<DataList<D>> recursiveComplete(DataList<D> rootDataList) {
        List<ListenableFuture<DataList<D>>> futures = new ArrayList<>();
        collectCompletionFutures(rootDataList, futures);
        return new CompleteAllOnGetFuture<D>(futures);
    }

    @SuppressWarnings("unchecked")
    private static <D extends Data> void collectCompletionFutures(DataList<D> dataList, List<ListenableFuture<DataList<D>>> futures) {
        futures.add(dataList.complete());
        for (D data : dataList.asList()) {
            if (data instanceof DataList)
                collectCompletionFutures((DataList<D>) data, futures);
        }
    }

    /**
     * A future which on get calls get on all its given futures and sets the value returned from the
     * first given future as its result.
     */
    private static class CompleteAllOnGetFuture<D extends Data> extends AbstractFuture<DataList<D>> {

        private final List<ListenableFuture<DataList<D>>> futures;

        public CompleteAllOnGetFuture(List<ListenableFuture<DataList<D>>> futures) {
            this.futures = new ArrayList<>(futures);
        }

        @Override
            public DataList<D> get() throws InterruptedException, ExecutionException {
            DataList<D> result = null;
            for (ListenableFuture<DataList<D>> future : futures) {
                if (result == null)
                    result = future.get();
                else
                    future.get();
            }
            set(result);
            return result;
        }

        @Override
            public DataList<D> get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            DataList<D> result = null;
            long timeLeft = unit.toMillis(timeout);
            long currentCallStart = SystemTimer.INSTANCE.milliTime();
            for (ListenableFuture<DataList<D>> future : futures) {
                if (result == null)
                    result = future.get(timeLeft, TimeUnit.MILLISECONDS);
                else
                    future.get(timeLeft, TimeUnit.MILLISECONDS);
                long currentCallEnd = SystemTimer.INSTANCE.milliTime();
                timeLeft -= (currentCallEnd - currentCallStart);
                if (timeLeft <= 0) break;
                currentCallStart = currentCallEnd;
            }
            set(result);
            return result;
        }

    }

}
