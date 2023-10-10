// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.client;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.yahoo.jdisc.application.BundleInstaller;
import com.yahoo.jdisc.application.ContainerActivator;
import com.yahoo.jdisc.application.OsgiFramework;
import org.junit.Test;
import org.osgi.framework.Bundle;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;

/**
 * @author Simon Thoresen Hult
 */
public class ClientDriverIntegrationTest {

    @Test
    public void requireThatApplicationClassInjectionWorks() throws Exception {
        MyModule module = new MyModule();
        ClientDriver.runApplicationWithOsgi("target/bundlecache_client", MyApplication.class, module);
        assertEquals(5, module.state);
    }

    private static class MyApplication implements ClientApplication {

        final Logger log = Logger.getLogger(MyApplication.class.getName());
        final ContainerActivator activator;
        final BundleInstaller installer;
        final MyModule module;

        @Inject
        MyApplication(ContainerActivator activator, OsgiFramework osgi, MyModule module) {
            this.activator = activator;
            this.installer = new BundleInstaller(osgi);
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
            try {
                List<Bundle> bundles = installer.installAndStart("cert-a.jar");
                Class<?> classObj = bundles.get(0).loadClass("com.yahoo.jdisc.bundle.a.CertificateA");
                log.info("Loaded '" + classObj.getName() + "'.");
            } catch (Exception e) {
                throw new AssertionError(e);
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
