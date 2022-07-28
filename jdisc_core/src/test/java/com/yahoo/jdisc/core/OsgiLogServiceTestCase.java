// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.yahoo.jdisc.application.OsgiFramework;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.jupiter.api.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.service.log.LogReaderService;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;


/**
 * @author Simon Thoresen Hult
 */
public class OsgiLogServiceTestCase {

    @Test
    void requireThatLogServiceIsRegistered() throws BundleException, InterruptedException {
        OsgiFramework osgi = TestDriver.newOsgiFramework();
        osgi.start();

        ServiceTracker<?, ?> logs = newTracker(osgi, LogService.class);
        ServiceTracker<?, ?> logReaders = newTracker(osgi, LogReaderService.class);
        assertEquals(1, logs.getTrackingCount());
        assertEquals(1, logReaders.getTrackingCount());

        OsgiLogService service = new OsgiLogService();
        service.start(osgi.bundleContext());

        assertEquals(2, logs.getTrackingCount());
        assertEquals(2, logReaders.getTrackingCount());
        osgi.stop();
    }

    @Test
    void requireThatLogServiceCanNotBeStartedTwice() throws BundleException {
        OsgiFramework osgi = TestDriver.newOsgiFramework();
        osgi.start();

        BundleContext ctx = osgi.bundleContext();
        OsgiLogService service = new OsgiLogService();
        service.start(ctx);

        try {
            service.start(ctx);
            fail();
        } catch (IllegalStateException e) {

        }

        osgi.stop();
    }

    @Test
    void requireThatLogServiceCanNotBeStoppedTwice() throws BundleException {
        OsgiFramework osgi = TestDriver.newOsgiFramework();
        osgi.start();

        BundleContext ctx = osgi.bundleContext();
        OsgiLogService service = new OsgiLogService();
        service.start(ctx);
        service.stop();

        try {
            service.stop();
            fail();
        } catch (NullPointerException e) {

        }

        osgi.stop();
    }

    @Test
    void requireThatUnstartedLogServiceCanNotBeStopped() throws BundleException {
        try {
            new OsgiLogService().stop();
            fail();
        } catch (NullPointerException e) {

        }
    }

    @Test
    void requireThatLogServiceCanNotStartWithoutBundleContext() throws BundleException {
        try {
            new OsgiLogService().start(null);
            fail();
        } catch (NullPointerException e) {

        }
    }

    @SuppressWarnings("unchecked")
    private static ServiceTracker<?,?> newTracker(OsgiFramework osgi, Class<?> trackedClass) {
        ServiceTracker<?,?> tracker = new ServiceTracker<>(osgi.bundleContext(), trackedClass, null);
        tracker.open();
        return tracker;
    }
}
