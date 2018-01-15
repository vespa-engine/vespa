// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Simon Thoresen Hult
 */
class OsgiLogManager implements LogService {

    private static final Object globalLock = new Object();
    private final CopyOnWriteArrayList<LogService> services = new CopyOnWriteArrayList<>();
    private final boolean configureLogLevel;
    private ServiceTracker<LogService,LogService> tracker;

    OsgiLogManager(boolean configureLogLevel) {
        this.configureLogLevel = configureLogLevel;
    }

    @SuppressWarnings("unchecked")
    public void install(final BundleContext osgiContext) {
        if (tracker != null) {
            throw new IllegalStateException("OsgiLogManager already installed.");
        }
        tracker = new ServiceTracker<>(osgiContext, LogService.class, new ServiceTrackerCustomizer<LogService,LogService>() {

            @Override
            public LogService addingService(ServiceReference<LogService> reference) {
                LogService service = osgiContext.getService(reference);
                services.add(service);
                return service;
            }

            @Override
            public void modifiedService(ServiceReference<LogService> reference, LogService service) {

            }

            @Override
            public void removedService(ServiceReference<LogService> reference, LogService service) {
                services.remove(service);
            }
        });
        tracker.open();
        synchronized (globalLock) {
            Logger root = Logger.getLogger("");
            if (configureLogLevel) {
                root.setLevel(Level.ALL);
            }
            for (Handler handler : root.getHandlers()) {
                root.removeHandler(handler);
            }
            root.addHandler(new OsgiLogHandler(this));
        }
    }

    public boolean uninstall() {
        if (tracker == null) {
            return false;
        }
        tracker.close(); // implicitly clears the services array
        tracker = null;
        return true;
    }

    @Override
    public void log(int level, String message) {
        log(null, level, message, null);
    }

    @Override
    public void log(int level, String message, Throwable throwable) {
        log(null, level, message, throwable);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void log(ServiceReference serviceRef, int level, String message) {
        log(serviceRef, level, message, null);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void log(ServiceReference serviceRef, int level, String message, Throwable throwable) {
        for (LogService obj : services) {
            obj.log(serviceRef, level, message, throwable);
        }
    }

    public static OsgiLogManager newInstance() {
        return new OsgiLogManager(System.getProperty("java.util.logging.config.file") == null);
    }
}
