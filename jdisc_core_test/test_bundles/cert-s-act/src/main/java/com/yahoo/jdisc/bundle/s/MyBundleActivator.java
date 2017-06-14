// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.bundle.s;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class MyBundleActivator implements BundleActivator {

    @Override
    public void start(BundleContext ctx) throws Exception {
        ServiceReference<?> ref = ctx.getServiceReference(RuntimeException.class);
        if (ref != null) {
            throw (RuntimeException)ctx.getService(ref);
        }
    }

    @Override
    public void stop(BundleContext ctx) throws Exception {
        ServiceReference<?> ref = ctx.getServiceReference(RuntimeException.class);
        if (ref != null) {
            throw (RuntimeException)ctx.getService(ref);
        }
    }
}
