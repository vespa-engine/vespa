// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.test;

import com.google.inject.Module;
import com.yahoo.jdisc.Container;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.application.Application;

import java.net.URI;

/**
 * @author Simon Thoresen Hult
 */
public final class NonWorkingRequest {

    private NonWorkingRequest() {
        // hide
    }

    /**
     * <p>Factory method to create a {@link Request} without an associated {@link Container}. The design of jDISC does
     * not allow this, so this method internally creates TestDriver, activates a Container, and creates a new Request
     * from that Container. Before returning, this method {@link Request#release() closes} the Request, and calls {@link
     * TestDriver#close()} on the TestDriver. This means that you MUST NOT attempt to access any Container features
     * through the created Request. This factory is only for directed feature tests that require a non-null
     * Request.</p>
     *
     * @param uri          The URI string to assign to the Request.
     * @param guiceModules The guice modules to inject into the {@link Application}.
     * @return A non-working Request.
     */
    public static Request newInstance(String uri, Module... guiceModules) {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi(guiceModules);
        driver.activateContainer(driver.newContainerBuilder());
        Request request = new Request(driver, URI.create(uri));
        request.release();
        driver.close();
        return request;
    }
}
