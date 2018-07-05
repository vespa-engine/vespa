// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.test;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.name.Names;
import com.yahoo.jdisc.Request;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


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
        Request request = NonWorkingRequest.newInstance("scheme://host/path", new AbstractModule() {

            @Override
            protected void configure() {
                bind(String.class).annotatedWith(Names.named("foo")).toInstance("bar");
            }
        });
        assertEquals("bar", request.container().getInstance(Key.get(String.class, Names.named("foo"))));
    }
}
