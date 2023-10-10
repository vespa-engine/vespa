// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.async;

import java.util.logging.Logger;

public class AsyncOperationImpl<T> implements AsyncOperation<T> {

    private static final Logger log = Logger.getLogger(AsyncOperationImpl.class.getName());
    private final String name;
    private final String description;
    private boolean resultGotten = false; // Ensures result is set only once.
    private boolean completed = false; // Indicates that operation is complete.
    private boolean failed = false;
    private T result;
    private Exception failure;
    private final AsyncOperationListenImpl<T> listenImpl;

    public AsyncOperationImpl(String name) {
        this(name, null);
    }
    public AsyncOperationImpl(String name, String description) {
        this.name = name;
        this.description = description;
        listenImpl = new AsyncOperationListenImpl<T>(this);
    }

    private boolean tagResultHandled() {
        synchronized (listenImpl) {
            if (resultGotten) {
                log.fine("Operation " + this + " got result attempted set twice. This may occasionally happen if multiple "
                       + "sources are set to possibly terminate operations, such as for example if there is a separate cancel or timeout "
                       + "handler.");
                return false;
            }
            resultGotten = true;
            return true;
        }
    }

    public void setFailure(Exception e) { setFailure(e, null); }
    public void setFailure(Exception e, T partialResult) {
        if (!tagResultHandled()) { return; }
        failed = true;
        failure = e;
        this.result = partialResult;
        completed = true;
        listenImpl.notifyListeners();
    }
    public void setResult(T result) {
        if (!tagResultHandled()) return;
        this.result = result;
        completed = true;
        listenImpl.notifyListeners();
    }

    @Override
    public String getName() { return name; }
    @Override
    public String getDescription() { return description; }
    @Override
    public String toString() { return "AsyncOperationImpl(" + name + ")"; }
    @Override
    public T getResult() { return result; }
    @Override
    public boolean cancel() { return false; }
    @Override
    public boolean isCanceled() { return false; }
    @Override
    public boolean isDone() { return completed; }
    @Override
    public boolean isSuccess() { return (completed && !failed); }
    @Override
    public Double getProgress() { return (completed ? 1.0 : null); }
    @Override
    public Exception getCause() { return failure; }
    @Override
    public void register(AsyncCallback<T> callback) {
        listenImpl.register(callback);
    }
    @Override
    public void unregister(AsyncCallback<T> callback) {
        listenImpl.unregister(callback);
    }

}
