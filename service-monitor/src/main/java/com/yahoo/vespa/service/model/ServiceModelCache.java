// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.service.model;

import com.yahoo.jdisc.Timer;
import com.yahoo.vespa.service.monitor.ServiceHostListener;
import com.yahoo.vespa.service.monitor.ServiceModel;
import com.yahoo.vespa.service.monitor.ServiceMonitor;

/**
 * Adds caching of a supplier of ServiceModel.
 *
 * @author hakonhall
 */
public class ServiceModelCache implements ServiceMonitor {
    public static final long EXPIRY_MILLIS = 10000;

    private final ServiceMonitor expensiveServiceMonitor;
    private final Timer timer;

    private volatile ServiceModel snapshot;
    private boolean updatePossiblyInProgress = false;

    private final Object updateMonitor = new Object();
    private long snapshotMillis;

    public ServiceModelCache(ServiceMonitor expensiveServiceMonitor, Timer timer) {
        this.expensiveServiceMonitor = expensiveServiceMonitor;
        this.timer = timer;
    }

    @Override
    public ServiceModel getServiceModelSnapshot() {
        if (snapshot == null) {
            synchronized (updateMonitor) {
                if (snapshot == null) {
                    takeSnapshot();
                }
            }
        } else if (expired()) {
            synchronized (updateMonitor) {
                if (updatePossiblyInProgress) {
                    return snapshot;
                }

                updatePossiblyInProgress = true;
            }

            try {
                takeSnapshot();
            } finally {
                synchronized (updateMonitor) {
                    updatePossiblyInProgress = false;
                }
            }
        }

        return snapshot;
    }

    @Override
    public void registerListener(ServiceHostListener listener) {
        expensiveServiceMonitor.registerListener(listener);
    }

    private void takeSnapshot() {
        snapshot = expensiveServiceMonitor.getServiceModelSnapshot();
        snapshotMillis = timer.currentTimeMillis();
    }

    private boolean expired() {
        return timer.currentTimeMillis() - snapshotMillis >= EXPIRY_MILLIS;
    }
}
