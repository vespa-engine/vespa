// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.yahoo.jdisc.test.TestDriver;
import org.junit.Test;
import org.osgi.framework.BundleException;

import static org.junit.Assert.fail;


/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class FelixFrameworkTestCase {

    @Test
    public void requireThatLifecycleWorks() throws BundleException {
        FelixFramework felix = TestDriver.newOsgiFramework();
        felix.start();
        felix.stop();
    }

    @Test
    public void requireThatStopWithoutStartDoesNotThrowException() throws BundleException {
        FelixFramework felix = TestDriver.newOsgiFramework();
        felix.stop();
    }

    @Test
    public void requireThatInstallCanThrowException() throws BundleException {
        FelixFramework felix = TestDriver.newOsgiFramework();
        felix.start();
        try {
            felix.installBundle("file:notfound.jar");
            fail();
        } catch (BundleException e) {

        }
        felix.stop();
    }
}
