// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.yahoo.jdisc.application.OsgiFramework;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.service.log.LogReaderService;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;


/**
 * @author Simon Thoresen Hult
 */
public class OsgiLogServiceTestCase {

    @Test
    public void requireThatLogServiceIsRegistered() throws BundleException, InterruptedException {
        OsgiFramework osgi = TestDriver.newOsgiFramework();
        osgi.start();

        ServiceTracker<?,?> logs = newTracker(osgi, LogService.class);
        ServiceTracker<?,?> logReaders = newTracker(osgi, LogReaderService.class);
        assertEquals(1, logs.getTrackingCount());
        assertEquals(1, logReaders.getTrackingCount());

        OsgiLogService service = new OsgiLogService();
        service.start(osgi.bundleContext());

        assertEquals(2, logs.getTrackingCount());
        assertEquals(2, logReaders.getTrackingCount());
        osgi.stop();
    }

    @Test
    public void requireThatLogServiceCanNotBeStartedTwice() throws BundleException {
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
    public void requireThatLogServiceCanNotBeStoppedTwice() throws BundleException {
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
    public void requireThatUnstartedLogServiceCanNotBeStopped() throws BundleException {
        try {
            new OsgiLogService().stop();
            fail();
        } catch (NullPointerException e) {

        }
    }

    @Test
    public void requireThatLogServiceCanNotStartWithoutBundleContext() throws BundleException {
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
