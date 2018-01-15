// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import org.osgi.framework.*;

/**
 * @author Simon Thoresen Hult
 */
class OsgiLogService {

    private ServiceRegistration<OsgiLogService> registration;

    public void start(BundleContext ctx) {
        if (registration != null) {
            throw new IllegalStateException();
        }
        ctx.addServiceListener(new ActivatorProxy(ctx));
        registration = ctx.registerService(OsgiLogService.class, this, null);
    }

    public void stop() {
        registration.unregister();
        registration = null;
    }

    private class ActivatorProxy implements ServiceListener {

        final BundleActivator activator = new org.apache.felix.log.Activator();
        final BundleContext ctx;

        ActivatorProxy(BundleContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void serviceChanged(ServiceEvent event) {
            if (ctx.getService(event.getServiceReference()) != OsgiLogService.this) {
                return;
            }
            switch (event.getType()) {
                case ServiceEvent.REGISTERED:
                    try {
                        activator.start(ctx);
                    } catch (Exception e) {
                        throw new RuntimeException("Exception thrown while starting " +
                                                   activator.getClass().getName() + ".", e);
                    }
                    break;
                case ServiceEvent.UNREGISTERING:
                    try {
                        activator.stop(ctx);
                    } catch (Exception e) {
                        throw new RuntimeException("Exception thrown while stopping " +
                                                   activator.getClass().getName() + ".", e);
                    }
                    break;
            }
        }
    }
}
