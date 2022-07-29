// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc;

import com.google.inject.AbstractModule;
import com.yahoo.jdisc.service.BindingSetNotFoundException;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;


/**
 * @author Simon Thoresen Hult
 */
public class ContainerTestCase {

    @Test
    void requireThatNewRequestsReferenceSameSnapshot() throws Exception {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        driver.activateContainer(driver.newContainerBuilder());
        Request foo = new Request(driver, URI.create("http://foo"));
        Request bar = new Request(foo, URI.create("http://bar"));
        assertNotNull(foo.container());
        assertSame(foo.container(), bar.container());
        foo.release();
        bar.release();
        driver.close();
    }

    @Test
    void requireThatInjectionWorks() throws BindingSetNotFoundException {
        final Object foo = new Object();
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi(new AbstractModule() {

            @Override
            protected void configure() {
                bind(Object.class).toInstance(foo);
            }
        });
        driver.activateContainer(driver.newContainerBuilder());
        Request request = new Request(driver, URI.create("http://host/path"));
        assertSame(foo, request.container().getInstance(Object.class));
        request.release();
        driver.close();
    }
}
