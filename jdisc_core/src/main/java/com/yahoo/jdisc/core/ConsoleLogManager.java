// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogReaderService;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

/**
 * @author <a href="mailto:vikasp@yahoo-inc.com">Vikas Panwar</a>
 */
class ConsoleLogManager {

    private final ConsoleLogListener listener = ConsoleLogListener.newInstance();
    private ServiceTracker<LogReaderService,LogReaderService> tracker;

    @SuppressWarnings("unchecked")
    public void install(final BundleContext osgiContext) {
        if (tracker != null) {
            throw new IllegalStateException("ConsoleLogManager already installed.");
        }
        tracker = new ServiceTracker<LogReaderService,LogReaderService>(osgiContext, LogReaderService.class.getName(),
            new ServiceTrackerCustomizer<LogReaderService,LogReaderService>() {

            @Override
            public LogReaderService addingService(ServiceReference<LogReaderService> reference) {
                LogReaderService service = osgiContext.getService(reference);
                service.addLogListener(listener);
                return service;
            }

            @Override
            public void modifiedService(ServiceReference<LogReaderService> reference, LogReaderService service) {

            }

            @Override
            public void removedService(ServiceReference<LogReaderService> reference, LogReaderService service) {
                service.removeLogListener(listener);
            }
        });
        tracker.open();
    }

    public boolean uninstall() {
        if (tracker == null) {
            return false;
        }
        tracker.close();
        tracker = null;
        return true;
    }
}
