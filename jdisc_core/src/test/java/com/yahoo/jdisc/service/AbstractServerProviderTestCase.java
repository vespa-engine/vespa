// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.service;

import com.google.inject.Inject;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author Simon Thoresen Hult
 */
public class AbstractServerProviderTestCase {

    @Test
    void requireThatAbstractClassIsAServerProvider() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        assertTrue(ServerProvider.class.isInstance(new MyServerProvider(driver)));
        assertTrue(driver.close());
    }

    @Test
    void requireThatAccessorsWork() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        MyServerProvider server = builder.getInstance(MyServerProvider.class);
        assertNotNull(server.container());
        assertTrue(driver.close());
    }

    private static class MyServerProvider extends AbstractServerProvider {

        @Inject
        public MyServerProvider(CurrentContainer container) {
            super(container);
        }

        @Override
        public void start() {

        }

        @Override
        public void close() {

        }
    }
}
