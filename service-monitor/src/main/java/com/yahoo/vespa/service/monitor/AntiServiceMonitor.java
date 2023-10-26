// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor;

import com.yahoo.vespa.service.duper.DuperModelManager;

/**
 * Interface that allows a thread to declare it must NOT access certain parts of the ServiceMonitor.
 *
 * @author hakonhall
 */
public interface AntiServiceMonitor {
    /**
     * Disallow the current thread to acquire the "duper model lock" (see {@link DuperModelManager}),
     * necessarily acquired by most of the {@link ServiceMonitor} methods, starting from now and
     * up until the returned region is closed.
     *
     * <p>For instance, if an application is activated the duper model is notified:  The duper model
     * will acquire a lock to update the model atomically, and while having that lock notify
     * the status service.  The status service will typically acquire an application lock and prune
     * hosts no longer part of the application.  If a thread were to try to acquire these locks
     * in the reverse order, it might become deadlocked. This method allows one to detect this
     * and throw an exception, causing it to be caught earlier or at least easier to debug.</p>
     */
    CriticalRegion disallowDuperModelLockAcquisition(String regionDescription);
}
