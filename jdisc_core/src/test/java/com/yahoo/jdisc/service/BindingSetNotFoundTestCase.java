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
public class BindingSetNotFoundTestCase {

    @Test
    void requireThatAccessorsWork() {
        BindingSetNotFoundException e = new BindingSetNotFoundException("foo");
        assertEquals("foo", e.bindingSet());
    }

    @Test
    void requireThatBindingSetNotFoundIsThrown() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi(new AbstractModule() {

            @Override
            protected void configure() {
                bind(BindingSetSelector.class).toInstance(new MySelector("foo"));
            }
        });
        driver.activateContainer(driver.newContainerBuilder());
        try {
            driver.newReference(URI.create("http://host"));
            fail();
        } catch (BindingSetNotFoundException e) {
            assertEquals("foo", e.bindingSet());
        }
        driver.close();
    }

    private static class MySelector implements BindingSetSelector {

        final String setName;

        MySelector(String setName) {
            this.setName = setName;
        }

        @Override
        public String select(URI uri) {
            return setName;
        }
    }
}
