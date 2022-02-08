// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.test;

import com.google.inject.AbstractModule;
import com.yahoo.jdisc.Request;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;


/**
 * @author Simon Thoresen Hult
 */
public class NonWorkingRequestTestCase {

    @Test
    public void requireThatFactoryMethodWorks() {
        assertNotNull(NonWorkingRequest.newInstance("scheme://host/path"));
    }

    @Test
    public void requireThatGuiceModulesAreInjected() {
        Object obj = new Object();
        Request request = NonWorkingRequest.newInstance("scheme://host/path", new AbstractModule() {

            @Override
            protected void configure() {
                bind(Object.class).toInstance(obj);
            }
        });
        assertSame(obj, request.container().getInstance(Object.class));
    }
}
