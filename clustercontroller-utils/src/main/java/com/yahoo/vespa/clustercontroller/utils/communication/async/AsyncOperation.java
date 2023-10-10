// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.async;

public interface AsyncOperation<T>   {

    /**
     * Attempt to cancel the given operation.
     * @return True if successfully cancelled. False if cancel is not supported, or operation already succeeded.
     */
    boolean cancel();

    /**
     * Register a callback to be called when this operation completes. If operation is already completed, this callback
     * will be called immediately upon registering. The same callback should not be registered multiple times. It is
     * suggested to throw an exception if that should happen. Otherwise you may get one or more calls to that component.
     */
    void register(AsyncCallback<T> callback);

    /**
     * Remove a callback from the list to be called when operation is completed. If callback has not already been called
     * at the time this function returns, it should never be called by this operation, unless re-registered.
     */
    void unregister(AsyncCallback<T> callback);

    /**
     * Get the name of the operation. Useful to identify what operation this is.
     */
    String getName();

    /**
     * Get a description of the operation. May be empty. If operation is complex one might want to use a short name for
     * simplicity, but have the whole request available if needed. In the HTTP case an application may for instance include
     * the URL in the name, and add the request headers to the description.
     */
    String getDescription();

    /**
     * Get the progress as a number between 0 and 1 where 0 means not started and 1 means operation is complete.
     * A return value of null indicates that the operation is unable to track progress.
     */
    Double getProgress();

    /**
     * Get the result of the operation.
     * Note that some operations may not have a result if the operation failed.
     */
    T getResult();

    /** Get the cause of an operation failing. Returns null on successful operations. */
    Exception getCause();

    /** Returns true if operation has been successfully cancelled. */
    boolean isCanceled();

    /** Returns true if operation has completed. Regardless of whether it was a success or a failure. */
    boolean isDone();

    /** Returns true if the operation was a success. */
    boolean isSuccess();

}
