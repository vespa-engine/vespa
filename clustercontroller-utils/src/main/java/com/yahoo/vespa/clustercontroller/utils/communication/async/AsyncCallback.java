// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.async;

public interface AsyncCallback<T>   {

    /** Callback indicating the given operation has completed. */
    void done(AsyncOperation<T> op);

}
