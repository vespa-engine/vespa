// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.yahoo.jdisc.test.TestDriver;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.service.log.LogListener;
import org.osgi.service.log.LogReaderService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;


/**
 * @author Simon Thoresen Hult
 */
public class ConsoleLogManagerTestCase {

    @Test
    void requireThatManagerCanNotBeInstalledTwice() throws BundleException {
        FelixFramework felix = TestDriver.newOsgiFramework();
        felix.start();

        ConsoleLogManager manager = new ConsoleLogManager();
        manager.install(felix.bundleContext());
        try {
            manager.install(felix.bundleContext());
            fail();
        } catch (IllegalStateException e) {
            assertEquals("ConsoleLogManager already installed.", e.getMessage());
        }

        felix.stop();
    }

    @Test
    void requireThatManagerCanBeUninstalledTwice() throws BundleException {
        FelixFramework felix = TestDriver.newOsgiFramework();
        felix.start();

        ConsoleLogManager manager = new ConsoleLogManager();
        assertFalse(manager.uninstall());
        manager.install(felix.bundleContext());
        assertTrue(manager.uninstall());
        assertFalse(manager.uninstall());

        felix.stop();
    }

    @Test
    void requireThatLogReaderServicesAreTracked() throws BundleException {
        FelixFramework felix = TestDriver.newOsgiFramework();
        felix.start();
        BundleContext ctx = felix.bundleContext();

        LogReaderService foo = Mockito.mock(LogReaderService.class);
        ctx.registerService(LogReaderService.class.getName(), foo, null);
        Mockito.verify(foo).addLogListener(Mockito.any(LogListener.class));

        LogReaderService bar = Mockito.mock(LogReaderService.class);
        ctx.registerService(LogReaderService.class.getName(), bar, null);
        Mockito.verify(bar).addLogListener(Mockito.any(LogListener.class));

        ConsoleLogManager manager = new ConsoleLogManager();
        manager.install(felix.bundleContext());

        Mockito.verify(foo, Mockito.times(2)).addLogListener(Mockito.any(LogListener.class));
        Mockito.verify(bar, Mockito.times(2)).addLogListener(Mockito.any(LogListener.class));

        LogReaderService baz = Mockito.mock(LogReaderService.class);
        ctx.registerService(LogReaderService.class.getName(), baz, null);
        Mockito.verify(baz, Mockito.times(2)).addLogListener(Mockito.any(LogListener.class));

        assertTrue(manager.uninstall());

        Mockito.verify(foo).removeLogListener(Mockito.any(LogListener.class));
        Mockito.verify(bar).removeLogListener(Mockito.any(LogListener.class));
        Mockito.verify(baz).removeLogListener(Mockito.any(LogListener.class));

        felix.stop();

        Mockito.verify(foo, Mockito.times(2)).removeLogListener(Mockito.any(LogListener.class));
        Mockito.verify(bar, Mockito.times(2)).removeLogListener(Mockito.any(LogListener.class));
        Mockito.verify(baz, Mockito.times(2)).removeLogListener(Mockito.any(LogListener.class));
    }
}
