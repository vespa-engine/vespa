// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.response;

import com.google.common.util.concurrent.ExecutionList;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * A list of data items created due to a processing request.
 * This list is itself a data item, allowing data items to be organized into a composite tree.
 * <p>
 * A data list can be frozen even though its child data items are not.
 * When a datalist is frozen the only permissible write operation is to add new items
 * to the end of the list.
 * <p>
 * Content in a frozen list may be returned to the requesting client immediately by the underlying engine,
 * even if the Response owning the list is not returned yet.
 *
 * @author bratseth
 */
public interface DataList<DATATYPE extends Data> extends Data {

    /**
     * Adds a child data item to this.
     *
     * @param data the data to add to this
     * @return the input data instance, for chaining
     */
    DATATYPE add(DATATYPE data);

    DATATYPE get(int index);

    /**
     * Returns the content of this as a List.
     * The returned list is either a read-only snapshot or an editable reference to the content of this.
     * If the returned list is editable and this is frozen, the only allowed operation is to add new items
     * to the end of the list.
     */
    List<DATATYPE> asList();

    /**
     * Returns the buffer of incoming/future data to this list.
     * This can be used to provide data to this list from other threads, after its creation,
     * and to consume, wait for or be notified upon the arrival of such data.
     * <p>
     * Some list instances do not support late incoming data,
     * such lists responds to <i>read</i> calls to IncomingData as expected and without
     * incurring any synchronization, and throws an exception on <i>write</i> calls.
     */
    IncomingData<DATATYPE> incoming();

    /**
     * Returns a future in which all incoming data in this has become available.
     * This has two uses:
     * <ul>
     * <li>Calling {@link #get} on this future will block (if necessary) until all incoming data has arrived,
     * transfer that data from the incoming buffer into this list and invoke any listeners on this event
     * on the calling thread.
     * <li>Adding a listener on this future will cause it to be called when completion().get() is called, <i>after</i>
     * the incoming data has been moved to this thread and <i>before</i> the get() call returns.
     * </ul>
     * <p>
     * Note that if no thread calls completed().get(), this future will never occur.
     * <p>
     * Any data list consumer who wishes to make sure it sees the complete data for this list
     * <i>must</i> call <code>dataList.complete().get()</code> before consuming this list.
     * If a guaranteed non-blocking call to this method is desired, register a listener on the future where all
     * data is available for draining (that is, on <code>dataList.incoming().completed()</code>)
     * and resume by calling this method from the listener.
     * <p>
     * Making this call on a list which does not support future data always returns immediately and
     * causes no memory synchronization cost.
     */
    ListenableFuture<DataList<DATATYPE>> complete();

    /**
     * Adds a listener which is invoked every time data is added to this list.
     * The listener is always invoked on the same thread which is adding the data,
     * and hence it can modify this list freely without synchronization.
     */
    void addDataListener(Runnable runnable);

    /**
     * Notify this list that is will never be accessed again, neither for read nor write.
     * Implementations can override this as an optimization to release any data held in the list
     * for garbage collection.
     *
     * This default implementation does nothing.
     */
    default void close() {};

}
