// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.yahoo.jdisc.test.TestDriver;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogReaderService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


/**
 * @author Simon Thoresen Hult
 * @author bjorncs
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
        ArrayList<LogEntry> logEntries = Collections.list(reader.getLog());
        assertTrue(logEntries.size() >= 4);

        assertLogContainsEntry("[jdk14] hello world", logEntries, now);
        assertLogContainsEntry("[slf4j] hello world", logEntries, now);
        assertLogContainsEntry("[log4j] hello world", logEntries, now);
        assertLogContainsEntry("[jcl] hello world", logEntries, now);

        assertTrue(driver.close());
    }

    private static void assertLogContainsEntry(String expectedMessage, List<LogEntry> logEntries, long expectedTimeGE)
    {
        LogEntry entry = logEntries.stream().filter(e -> e.getMessage().equals(expectedMessage)).findFirst()
                .orElseThrow(() -> new AssertionError("Could not find log entry with messsage: " + expectedMessage));

        assertNull(entry.getBundle());
        assertNotNull(entry.getServiceReference());
        assertEquals(OsgiLogHandler.toServiceLevel(Level.INFO), entry.getLevel());
        assertNull(entry.getException());
        assertTrue(expectedTimeGE <= entry.getTime());
    }
}
