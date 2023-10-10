// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.bundle.g_act;

import com.yahoo.jdisc.service.CurrentContainer;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

/**
 * @author Simon Thoresen Hult
 */
public class MyBundleActivator implements BundleActivator {

    private MyService service;
    private ServiceRegistration<?> registration;

    @Override
    public void start(BundleContext ctx) throws Exception {
        ServiceReference<?> containerRef = ctx.getServiceReference(CurrentContainer.class.getName());
        service = new MyService((CurrentContainer)ctx.getService(containerRef));
        registration = ctx.registerService(MyService.class.getName(), service, null);
    }

    @Override
    public void stop(BundleContext ctx) throws Exception {
        if (registration != null) {
            registration.unregister();
        }
        if (service != null) {
            service.release();
        }
    }
}
