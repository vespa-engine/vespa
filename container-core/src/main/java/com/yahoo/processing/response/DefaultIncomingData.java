// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.response;

import com.yahoo.collections.Tuple2;
import com.yahoo.concurrent.CompletableFutures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * The default incoming data implementation
 *
 * @author bratseth
 */
public class DefaultIncomingData<DATATYPE extends Data> implements IncomingData<DATATYPE> {

    private DataList<DATATYPE> owner = null;

    private final CompletableFuture<DataList<DATATYPE>> completionFuture;

    private final List<DATATYPE> dataList = new ArrayList<>();

    private List<Tuple2<Runnable,Executor>> newDataListeners = null;

    /** Whether this is completed, such that no more data can be added */
    private boolean complete = false;

    /** Creates an instance which must be assigned an owner after creation */
    public DefaultIncomingData() {
        this(null);
    }

    public DefaultIncomingData(DataList<DATATYPE> owner) {
        assignOwner(owner);
        completionFuture = new CompletableFuture<>();
    }

    /** Assigns the owner of this. Throws an exception if the owner is already set. */
    public final void assignOwner(DataList<DATATYPE> owner) {
        if (this.owner != null) throw new NullPointerException("Owner of " + this + " was already assigned");
        this.owner = owner;
    }

    @Override
    public DataList<DATATYPE> getOwner() {
        return owner;
    }

    @Override public CompletableFuture<DataList<DATATYPE>> completedFuture() { return completionFuture; }

    /** Returns whether the data in this is complete */
    @Override
    public synchronized boolean isComplete() {
        return complete;
    }

    /** Adds new data and marks this as completed */
    @Override
    public synchronized void addLast(DATATYPE data) {
        addLast(Collections.singletonList(data));
    }

    /** Adds new data without completing this */
    @Override
    public synchronized void add(DATATYPE data) {
        add(Collections.singletonList(data));
    }

    /** Adds new data and marks this as completed */
    @Override
    public synchronized void addLast(List<DATATYPE> data) {
        add(data);
        markComplete();
    }

    /** Adds new data without completing this */
    @Override
    public synchronized void add(List<DATATYPE> data) {
        if (complete) throw new IllegalStateException("Attempted to add data to completed " + this);

        dataList.addAll(data);
        notifyDataListeners();
    }

    /** Marks this as completed and notify any listeners */
    @Override
    public synchronized void markComplete() {
        complete = true;
        completionFuture.complete(owner);
    }

    /**
     * Gets and removes all the data currently available in this.
     * The returned list is a modifiable fresh instance owned by the caller.
     */
    public synchronized List<DATATYPE> drain() {
        List<DATATYPE> dataListCopy = new ArrayList<>(dataList);
        dataList.clear();
        return dataListCopy;
    }

    @Override
    public void addNewDataListener(Runnable listener, Executor executor) {
        synchronized (this) {
            if (newDataListeners == null)
                newDataListeners = new ArrayList<>();
            newDataListeners.add(new Tuple2<>(listener, executor));
            if (dataList.isEmpty()) return;
        }
        notifyDataListeners();
    }

    private void notifyDataListeners() {
        if (newDataListeners == null) return;
        for (Tuple2<Runnable, Executor> listener : newDataListeners) {
            listener.second.execute(listener.first);
        }
    }

    @Override
    public String toString() {
        return "incoming: " + (complete ? "complete" : "incomplete") + ", data " + dataList;
    }

}
