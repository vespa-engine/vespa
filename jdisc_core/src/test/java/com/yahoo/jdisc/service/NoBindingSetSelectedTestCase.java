// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.service;

import com.google.inject.AbstractModule;
import com.yahoo.jdisc.application.BindingSetSelector;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;


/**
 * @author Simon Thoresen Hult
 */
public class NoBindingSetSelectedTestCase {

    @Test
    void requireThatAccessorsWork() {
        URI uri = URI.create("http://host/path");
        NoBindingSetSelectedException e = new NoBindingSetSelectedException(uri);
        assertEquals(uri, e.uri());
    }

    @Test
    void requireThatNoBindingSetSelectedIsThrown() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi(new AbstractModule() {

            @Override
            protected void configure() {
                bind(BindingSetSelector.class).toInstance(new MySelector());
            }
        });
        driver.activateContainer(driver.newContainerBuilder());
        URI uri = URI.create("http://host");
        try {
            driver.newReference(uri);
            fail();
        } catch (NoBindingSetSelectedException e) {
            assertEquals(uri, e.uri());
        }
        driver.close();
    }

    private static class MySelector implements BindingSetSelector {

        @Override
        public String select(URI uri) {
            return null;
        }
    }

}
