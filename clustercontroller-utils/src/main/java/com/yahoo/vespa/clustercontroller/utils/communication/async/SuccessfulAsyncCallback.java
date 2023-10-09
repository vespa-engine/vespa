// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.async;

public abstract class SuccessfulAsyncCallback<Source, Target> implements AsyncCallback<Source> {

    private final AsyncOperationImpl<Target> target;

    public SuccessfulAsyncCallback(final AsyncOperationImpl<Target> target) {
        this.target = target;
    }

    public void done(AsyncOperation<Source> sourceOp) {
        if (sourceOp.isSuccess()) {
            successfullyDone(sourceOp);
        } else {
            target.setFailure(sourceOp.getCause());
        }
    }

    public abstract void successfullyDone(AsyncOperation<Source> op);

}
