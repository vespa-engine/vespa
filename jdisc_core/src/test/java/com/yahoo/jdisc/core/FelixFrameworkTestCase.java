// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.yahoo.jdisc.test.TestDriver;
import org.junit.jupiter.api.Test;
import org.osgi.framework.BundleException;

import static org.junit.jupiter.api.Assertions.fail;


/**
 * @author Simon Thoresen Hult
 */
public class FelixFrameworkTestCase {

    @Test
    void requireThatLifecycleWorks() throws BundleException {
        FelixFramework felix = TestDriver.newOsgiFramework();
        felix.start();
        felix.stop();
    }

    @Test
    void requireThatStopWithoutStartDoesNotThrowException() throws BundleException {
        FelixFramework felix = TestDriver.newOsgiFramework();
        felix.stop();
    }

    @Test
    void requireThatInstallCanThrowException() throws BundleException {
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
