// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.osgi.test.client;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import com.yahoo.osgi.test.counterservice.CounterService;

/**
 * A bundle which is a client calling to another bundle.
 *
 * @author bratseth
 */
public class Client implements BundleActivator {

    @SuppressWarnings("unchecked")
    public void start(BundleContext context) {
        ServiceReference counterServiceReference=context.getServiceReference(CounterService.class.getName());
        CounterService counterService =(CounterService)context.getService(counterServiceReference);
        assertEquals(0, counterService.getCounter());
        counterService.incrementCounter();
        assertEquals(1, counterService.getCounter());
    }

    public void stop(BundleContext context) {}

    private void assertEquals(Object correct,Object test) {
        if (!correct.equals(test))
            throw new RuntimeException("Expected " + correct + ", got " + test);
    }

}
