// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.async;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.logging.Logger;

public class AsyncOperationListenImpl<T> {

    private static final Logger log = Logger.getLogger(AsyncOperationListenImpl.class.getName());
    private final Collection<AsyncCallback<T>> listeners = new HashSet<>();
    private boolean listenersNotified = false;
    private final AsyncOperation<T> op;

    protected AsyncOperationListenImpl(AsyncOperation<T> op) {
        this.op = op;
    }

    public void register(AsyncCallback<T> callback) {
        synchronized (listeners) {
            listeners.add(callback);
            if (listenersNotified) callback.done(op);
        }
    }
    public void unregister(AsyncCallback<T> callback) {
        synchronized (listeners) {
            listeners.remove(callback);
        }
    }

    public void notifyListeners() {
        synchronized (listeners) {
            if (listenersNotified) return;
            for(AsyncCallback<T> callback : listeners) {
                try{
                    callback.done(op);
                } catch (RuntimeException e) {
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    log.warning("Callback '" + callback + "' threw exception on notify. Should not happen:\n" + sw);
                }
            }
            listenersNotified = true;
        }
    }

}
