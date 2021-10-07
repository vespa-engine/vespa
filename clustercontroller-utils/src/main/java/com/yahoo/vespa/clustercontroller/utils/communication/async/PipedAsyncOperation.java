// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.async;

public abstract class PipedAsyncOperation<S, T> extends RedirectedAsyncOperation<S, T> {

    private T result;

    public PipedAsyncOperation(AsyncOperation<S> source) {
        super(source);
        setOnCompleteTask(new AsyncCallback<S>() {
            @Override
            public void done(AsyncOperation<S> op) {
                result = convertResult(op.getResult());
            }
        });
    }

    public abstract T convertResult(S result);

    @Override
    public T getResult() {
        return result;
    }

}
