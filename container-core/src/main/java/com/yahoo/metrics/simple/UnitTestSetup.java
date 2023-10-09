// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics.simple;

import com.yahoo.metrics.ManagerConfig;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Common code for running unit tests of simplemetrics
 *
 * @author ean
 */
public class UnitTestSetup {

    MetricManager metricManager;
    MetricReceiver receiver;
    ObservableUpdater updater;

    static class ObservableUpdater extends MetricUpdater {
        CountDownLatch gotData = new CountDownLatch(1);
        private volatile boolean hasBeenAccessed = false;

        @Override
        public Bucket createGenerationInstance(Bucket previous) {
            if (hasBeenAccessed) {
                gotData.countDown();
            }
            return super.createGenerationInstance(previous);
        }

        @Override
        public Bucket update(Bucket current, Sample x) {
            hasBeenAccessed = true;
            return super.update(current, x);
        }
    }

    void init() {
        updater = new ObservableUpdater();
        metricManager = MetricManager.constructWithCustomUpdater(new ManagerConfig(new ManagerConfig.Builder()), updater);
        receiver = metricManager.get();
    }

    void fini() {
        receiver = null;
        metricManager.deconstruct();
        metricManager = null;
        updater = null;
    }

    public Bucket getUpdatedSnapshot() throws InterruptedException {
        updater.gotData.await(10, TimeUnit.SECONDS);
        Bucket s = receiver.getSnapshot();
        long startedWaitingForSnapshot = System.currentTimeMillis();
        // just waiting for the correct snapshot being constructed (yes, this is
        // necessary)
        while (s == null || s.entrySet().size() == 0) {
            if (System.currentTimeMillis() - startedWaitingForSnapshot > (10L * 1000L)) {
                throw new RuntimeException("Test timed out.");
            }
            Thread.sleep(10);
            s = receiver.getSnapshot();
        }
        return s;
    }

    public MetricReceiver getReceiver() {
        return receiver;
    }
}
