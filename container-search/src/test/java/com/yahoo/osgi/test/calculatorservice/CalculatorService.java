// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.osgi.test.calculatorservice;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import com.yahoo.osgi.test.Calculator;

/**
 * An implementation of an interface which is not part of the bundle
 *
 * @author bratseth
 */
public class CalculatorService implements Calculator, BundleActivator {

    private ServiceRegistration calculatorServiceRegistration;

    public int add(int a,int b) {
        return a+b;
    }

    public void start(BundleContext context) {
        calculatorServiceRegistration=context.registerService(Calculator.class.getName(), this, null);
    }

    public void stop(BundleContext context) {
        calculatorServiceRegistration.unregister();
    }

}
