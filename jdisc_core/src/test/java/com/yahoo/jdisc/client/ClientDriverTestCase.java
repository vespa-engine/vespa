// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.client;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * @author Simon Thoresen Hult
 */
public class ClientDriverTestCase {

    @Test
    void requireThatApplicationInstanceInjectionWorks() throws Exception {
        MyModule module = new MyModule();
        ClientDriver.runApplication(new MyApplication(module));
        assertEquals(5, module.state);
    }

    @Test
    void requireThatApplicationClassInjectionWorks() throws Exception {
        MyModule module = new MyModule();
        ClientDriver.runApplication(MyApplication.class, module);
        assertEquals(5, module.state);
    }

    private static class MyApplication implements ClientApplication {

        final MyModule module;

        @Inject
        MyApplication(MyModule module) {
            this.module = module;
            module.state = 1;
        }

        @Override
        public void start() {
            if (++module.state != 2) {
                throw new IllegalStateException();
            }
        }

        @Override
        public void run() {
            if (++module.state != 3) {
                throw new IllegalStateException();
            }
        }

        @Override
        public void stop() {
            if (++module.state != 4) {
                throw new IllegalStateException();
            }
        }

        @Override
        public void destroy() {
            if (++module.state != 5) {
                throw new IllegalStateException();
            }
        }
    }

    private static class MyModule extends AbstractModule {

        int state = 0;

        @Override
        protected void configure() {
            bind(MyModule.class).toInstance(this);
        }
    }
}
