// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.bundle;

import com.yahoo.jdisc.service.CurrentContainer;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

/**
 * @author Simon Thoresen Hult
 */
public class MyBundleActivator implements BundleActivator {

    private ServiceRegistration<?> registration;

    @Override
    public void start(BundleContext ctx) throws Exception {
        ServiceReference<?> seviceRef = ctx.getServiceReference(CurrentContainer.class.getName());
        if (seviceRef == null) {
            throw new IllegalStateException("Service reference '" + CurrentContainer.class.getName() + "' not found.");
        }
        Object service = ctx.getService(seviceRef);
        if (service == null) {
            throw new IllegalStateException("Service '" + CurrentContainer.class.getName() + "' not found.");
        }
        if (!(service instanceof CurrentContainer)) {
            throw new IllegalStateException("Expected " + CurrentContainer.class + ", got " + service.getClass() + ".");
        }
        registration = ctx.registerService(MyService.class.getName(), new MyService(), null);
    }

    @Override
    public void stop(BundleContext ctx) throws Exception {
        if (registration != null) {
            registration.unregister();
        }
    }
}
