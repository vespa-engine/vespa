// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.yahoo.jdisc.test.TestDriver;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogReaderService;

import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
public class OsgiLogServiceIntegrationTest {

    @Test
    @SuppressWarnings("unchecked")
    public void requireThatAllSupportedLogFrameworksAreConfigured() throws Exception {
        // need to explicitly set log level of root logger since integration suite now provides a logger config file,
        // which disables that setLevel() call of the OsgiLogManager.
        Logger.getLogger("").setLevel(Level.INFO);

        long now = System.currentTimeMillis();
        TestDriver driver = TestDriver.newApplicationBundleInstance("app-h-log.jar", false);
        BundleContext ctx = driver.osgiFramework().bundleContext();
        ServiceReference<?> ref = ctx.getServiceReference(LogReaderService.class.getName());
        LogReaderService reader = (LogReaderService)ctx.getService(ref);
        Enumeration<LogEntry> log = (Enumeration<LogEntry>)reader.getLog();

        assertEntry(Level.INFO, "[jdk14] hello world", null, now, log);
        assertEntry(Level.INFO, "[slf4j] hello world", null, now, log);
        assertEntry(Level.INFO, "[log4j] hello world", null, now, log);
        assertEntry(Level.INFO, "[jcl] hello world", null, now, log);

        assertTrue(driver.close());
    }

    private static void assertEntry(Level expectedLevel, String expectedMessage, Throwable expectedException,
                                    long expectedTimeGE, Enumeration<LogEntry> log)
    {
        assertTrue(log.hasMoreElements());
        LogEntry entry = log.nextElement();
        assertNotNull(entry);
        System.err.println("log entry: "+entry.getMessage()+" bundle="+entry.getBundle());
        assertEquals(expectedMessage, entry.getMessage());
        assertNull(entry.getBundle());
        assertNotNull(entry.getServiceReference());
        assertEquals(OsgiLogHandler.toServiceLevel(expectedLevel), entry.getLevel());
        assertEquals(expectedException, entry.getException());
        assertTrue(expectedTimeGE <= entry.getTime());
    }
}
