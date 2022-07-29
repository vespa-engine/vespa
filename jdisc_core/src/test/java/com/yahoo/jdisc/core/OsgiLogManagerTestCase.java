// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.yahoo.jdisc.test.TestDriver;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.log.LogService;

import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;


/**
 * @author Simon Thoresen Hult
 */
public class OsgiLogManagerTestCase {

    @Test
    void requireThatAllLogMethodsAreImplemented() throws BundleException {
        FelixFramework felix = TestDriver.newOsgiFramework();
        felix.start();

        BundleContext ctx = felix.bundleContext();
        OsgiLogManager manager = new OsgiLogManager(true);
        manager.install(ctx);
        MyLogService service = new MyLogService();
        ctx.registerService(LogService.class.getName(), service, null);

        manager.log(2, "a");
        assertLast(service, null, 2, "a", null);

        Throwable t1 = new Throwable();
        manager.log(4, "b", t1);
        assertLast(service, null, 4, "b", t1);

        ServiceReference<?> ref1 = Mockito.mock(ServiceReference.class);
        manager.log(ref1, 8, "c");
        assertLast(service, ref1, 8, "c", null);

        ServiceReference<?> ref2 = Mockito.mock(ServiceReference.class);
        Throwable t2 = new Throwable();
        manager.log(ref2, 16, "d", t2);
        assertLast(service, ref2, 16, "d", t2);

        manager.uninstall();
        felix.stop();
    }

    @Test
    void requireThatLogManagerWritesToAllRegisteredLogServices() throws BundleException {
        FelixFramework felix = TestDriver.newOsgiFramework();
        felix.start();

        BundleContext ctx = felix.bundleContext();
        MyLogService foo = new MyLogService();
        ServiceRegistration<LogService> fooReg = ctx.registerService(LogService.class, foo, null);

        OsgiLogManager manager = new OsgiLogManager(true);
        manager.install(ctx);

        ServiceReference<?> ref1 = Mockito.mock(ServiceReference.class);
        Throwable t1 = new Throwable();
        manager.log(ref1, 2, "a", t1);
        assertLast(foo, ref1, 2, "a", t1);

        MyLogService bar = new MyLogService();
        ServiceRegistration<LogService> barReg = ctx.registerService(LogService.class, bar, null);

        ServiceReference<?> ref2 = Mockito.mock(ServiceReference.class);
        Throwable t2 = new Throwable();
        manager.log(ref2, 4, "b", t2);
        assertLast(foo, ref2, 4, "b", t2);
        assertLast(bar, ref2, 4, "b", t2);

        MyLogService baz = new MyLogService();
        ServiceRegistration<LogService> bazReg = ctx.registerService(LogService.class, baz, null);

        ServiceReference<?> ref3 = Mockito.mock(ServiceReference.class);
        Throwable t3 = new Throwable();
        manager.log(ref3, 8, "c", t3);
        assertLast(foo, ref3, 8, "c", t3);
        assertLast(bar, ref3, 8, "c", t3);
        assertLast(baz, ref3, 8, "c", t3);

        fooReg.unregister();

        ServiceReference<?> ref4 = Mockito.mock(ServiceReference.class);
        Throwable t4 = new Throwable();
        manager.log(ref4, 16, "d", t4);
        assertLast(foo, ref3, 8, "c", t3);
        assertLast(bar, ref4, 16, "d", t4);
        assertLast(baz, ref4, 16, "d", t4);

        barReg.unregister();

        ServiceReference<?> ref5 = Mockito.mock(ServiceReference.class);
        Throwable t5 = new Throwable();
        manager.log(ref5, 32, "e", t5);
        assertLast(foo, ref3, 8, "c", t3);
        assertLast(bar, ref4, 16, "d", t4);
        assertLast(baz, ref5, 32, "e", t5);

        bazReg.unregister();

        ServiceReference<?> ref6 = Mockito.mock(ServiceReference.class);
        Throwable t6 = new Throwable();
        manager.log(ref6, 64, "f", t6);
        assertLast(foo, ref3, 8, "c", t3);
        assertLast(bar, ref4, 16, "d", t4);
        assertLast(baz, ref5, 32, "e", t5);

        manager.uninstall();
        felix.stop();
    }

    @Test
    void requireThatRootLoggerModificationCanBeDisabled() throws BundleException {
        Logger logger = Logger.getLogger("");
        logger.setLevel(Level.WARNING);

        new OsgiLogManager(false).install(Mockito.mock(BundleContext.class));
        assertEquals(Level.WARNING, logger.getLevel());

        new OsgiLogManager(true).install(Mockito.mock(BundleContext.class));
        assertEquals(Level.ALL, logger.getLevel());
    }

    @Test
    void requireThatRootLoggerLevelIsModifiedIfNoLoggerConfigIsGiven() {
        Logger logger = Logger.getLogger("");
        logger.setLevel(Level.WARNING);

        OsgiLogManager.newInstance().install(Mockito.mock(BundleContext.class));

        assertNull(System.getProperty("java.util.logging.config.file"));
        assertEquals(Level.ALL, logger.getLevel());
    }

    private static void assertLast(MyLogService service, ServiceReference<?> ref, int level, String message, Throwable t) {
        assertSame(ref, service.lastServiceReference);
        assertEquals(level, service.lastLevel);
        assertEquals(message, service.lastMessage);
        assertSame(t, service.lastThrowable);
    }

    @SuppressWarnings("rawtypes")
    private static class MyLogService implements LogService {

        ServiceReference lastServiceReference;
        int lastLevel;
        String lastMessage;
        Throwable lastThrowable;

        @Override
        public void log(int level, String message) {
            log(null, level, message, null);
        }

        @Override
        public void log(int level, String message, Throwable throwable) {
            log(null, level, message, throwable);
        }

        @Override
        public void log(ServiceReference serviceReference, int level, String message) {
            log(serviceReference, level, message, null);
        }

        @Override
        public void log(ServiceReference serviceReference, int level, String message, Throwable throwable) {
            lastServiceReference = serviceReference;
            lastLevel = level;
            lastMessage = message;
            lastThrowable = throwable;
        }
    }
}
