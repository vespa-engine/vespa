// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.response;

import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * A data list own once instance of this which can be used to provide data asynchronously to the list,
 * and consume, wait for or be notified upon the arrival of such data.
 *
 * @author bratseth
 */
public interface IncomingData<DATATYPE extends Data> {

    /**
     * Returns the owner (target DataList) of this.
     * Note that accessing the owner from the thread producing incoming data
     * is generally *not* thread safe.
     */
    DataList<DATATYPE> getOwner();

    /**
     * Returns a future in which all the incoming data that will be produced in this is available.
     * Listeners on this are invoked on the thread producing the incoming data (or a thread spawned from it),
     * which in general is separate from the thread using the data list. Hence, listeners on this even cannot
     * in general assume that they may modify the data list or the request.
     * <p>
     * The data is not {@link #drain drained} into the owner of this by this method. That must be done
     * by the thread using the data list.
     * <p>
     * This return the list owning this for convenience.
     */
    ListenableFuture<DataList<DATATYPE>> completed();

    /**
     * Returns whether this is complete
     */
    boolean isComplete();

    /**
     * Add new data and mark this as completed
     *
     * @throws IllegalStateException if this is already complete or does not allow writes
     */
    void addLast(DATATYPE data);

    /**
     * Add new data without completing this
     *
     * @throws IllegalStateException if this is already complete or does not allow writes
     */
    void add(DATATYPE data);

    /**
     * Add new data and mark this as completed
     *
     * @throws IllegalStateException if this is already complete or does not allow writes
     */
    void addLast(List<DATATYPE> data);

    /**
     * Add new data without completing this.
     *
     * @throws IllegalStateException if this is already complete or does not allow writes
     */
    void add(List<DATATYPE> data);

    /**
     * Mark this as completed and notify any listeners. If this is already complete this method does nothing.
     */
    void markComplete();

    /**
     * Get and remove all the data currently available in this
     */
    List<DATATYPE> drain();

    /**
     * Add a listener which will be invoked every time new data is added to this.
     * This listener may be invoked at any time in any thread, any thread synchronization is left
     * to the listener itself
     */
    void addNewDataListener(Runnable listener, Executor executor);

    /**
     * Creates a null implementation of this which is empty and complete at creation:
     * <ul>
     * <li>Provides immediate return without incurring any memory synchronization for
     * any read method.
     * <li>Throws an exception on any write method
     * </ul>
     * <p>
     * This allows consumers to check for completion the same way whether or not the data list in question
     * supports asynchronous addition of data, and without incurring unnecessary costs.
     */
    final class NullIncomingData<DATATYPE extends Data> implements IncomingData<DATATYPE> {

        private DataList<DATATYPE> owner;
        private final ImmediateFuture<DATATYPE> completionFuture;

        public NullIncomingData(DataList<DATATYPE> owner) {
            this.owner = owner;
            completionFuture = new ImmediateFuture<>(owner);
        }

        public ListenableFuture<DataList<DATATYPE>> completed() {
            return completionFuture;
        }

        @Override
        public DataList<DATATYPE> getOwner() {
            return owner;
        }

        /**
         * Returns true
         */
        @Override
        public boolean isComplete() {
            return true;
        }

        /**
         * @throws IllegalStateException as this is read only
         */
        public void addLast(DATATYPE data) {
            throw new IllegalStateException(owner + " does not support adding data asynchronously");
        }

        /**
         * @throws IllegalStateException as this is read only
         */
        public void add(DATATYPE data) {
            throw new IllegalStateException(owner + " does not support adding data asynchronously");
        }

        /**
         * @throws IllegalStateException as this is read only
         */
        public void addLast(List<DATATYPE> data) {
            throw new IllegalStateException(owner + " does not support adding data asynchronously");
        }

        /**
         * @throws IllegalStateException as this is read only
         */
        public void add(List<DATATYPE> data) {
            throw new IllegalStateException(owner + " does not support adding data asynchronously");
        }

        /**
         * Do nothing as this is already complete
         */
        public void markComplete() {
        }

        public List<DATATYPE> drain() {
            return Collections.emptyList();
        }

        /**
         * Adds a new data listener to this - this is a no-op
         * as new data can never be added to this implementation.
         */
        public void addNewDataListener(Runnable listener, Executor executor) { }

        public String toString() {
            return "(no incoming)";
        }

        /**
         * A future which is always done and incurs no synchronization.
         * This is semantically the same as Futures.immediateFuture but contrary to it,
         * this never causes any memory synchronization when accessed.
         */
        public static class ImmediateFuture<DATATYPE extends Data> extends AbstractFuture<DataList<DATATYPE>> {

            private DataList<DATATYPE> owner;

            public ImmediateFuture(DataList<DATATYPE> owner) {
                this.owner = owner; // keep here to avoid memory synchronization for access
                set(owner); // Signal completion (for future listeners)
            }

            @Override
            public boolean cancel(boolean b) {
                return false;
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public boolean isDone() {
                return true;
            }

            @Override
            public DataList<DATATYPE> get() {
                return owner;
            }

            @Override
            public DataList<DATATYPE> get(long l, TimeUnit timeUnit) {
                return owner;
            }

        }

    }

}
