// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.provider;

import com.google.common.util.concurrent.ExecutionList;

import java.util.concurrent.Executor;

/**
 * A convenience superclass for listenable freezables.
 *
 * @author bratseth
 */
public class ListenableFreezableClass extends FreezableClass implements ListenableFreezable {

    private ExecutionList executionList = new ExecutionList();

    /**
     * Freezes this class to prevent further changes. Override this to freeze internal data
     * structures and dependent objects. Overrides must call super.
     * Calling freeze on an already frozen registry must have no effect.
     * <p>
     * Notifies listeners that freezing has happened.
     */
    public synchronized void freeze() {
        super.freeze();
        executionList.execute();
    }

    /** Adds a listener which will be invoked when this has become frozen. */
    @Override
    public void addFreezeListener(Runnable runnable, Executor executor) {
        executionList.add(runnable, executor);
    }

    /** Clones this. The clone is <i>not</i> frozen and has no listeners. */
    @Override
    public ListenableFreezableClass clone() {
        ListenableFreezableClass clone = (ListenableFreezableClass)super.clone();
        clone.executionList = new ExecutionList();
        return clone;
    }

}
