// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.osgi.test.counterservice;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

/**
 * A service which must be imported by another bundle to be used
 *
 * @author bratseth
 */
public class CounterServiceImpl implements CounterService, BundleActivator {

    private int counter=0;

    private ServiceRegistration counterServiceRegistration;

    public void start(BundleContext context) {
        counterServiceRegistration=context.registerService(CounterService.class.getName(), this, null);
    }

    public void stop(BundleContext context) {
        counterServiceRegistration.unregister();
    }

    public int getCounter() {
        return counter;
    }

    public void incrementCounter() {
        counter++;
    }

}
