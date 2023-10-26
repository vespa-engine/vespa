// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.async;

/**
 * Utility class in order to wrap com.yahoo.vespa.clustercontroller.utils.communication.async operation callbacks. Useful when translating com.yahoo.vespa.clustercontroller.utils.communication.async operations returning JSON to com.yahoo.vespa.clustercontroller.utils.communication.async operations returning specific values.
 */
public abstract class RedirectedAsyncOperation<S, T> implements AsyncOperation<T> {

    protected final AsyncOperation<S> source;
    private final AsyncOperationListenImpl<T> listenImpl;

    public RedirectedAsyncOperation(AsyncOperation<S> source) {
        this.source = source;
        this.listenImpl = new AsyncOperationListenImpl<>(this);
        source.register(new AsyncCallback<S>() {
            @Override
            public void done(AsyncOperation<S> op) { notifyDone(); }
        });
    }

    private void notifyDone() {
        listenImpl.notifyListeners();
    }

    @Override
    public String getName() { return source.getName(); }

    @Override
    public String getDescription() { return source.getDescription(); }

    @Override
    public boolean cancel() { return source.cancel(); }

    @Override
    public boolean isCanceled() { return source.isCanceled(); }

    @Override
    public boolean isDone() { return source.isDone(); }

    @Override
    public boolean isSuccess() { return source.isSuccess(); }

    @Override
    public Double getProgress() { return source.getProgress(); }

    @Override
    public Exception getCause() { return source.getCause(); }

    @Override
    public void register(AsyncCallback<T> callback) {
        listenImpl.register(callback);
    }

    @Override
    public void unregister(AsyncCallback<T> callback) {
        listenImpl.unregister(callback);
    }

}
