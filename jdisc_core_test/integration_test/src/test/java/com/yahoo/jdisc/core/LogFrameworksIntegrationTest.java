// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.yahoo.io.IOUtils;
import com.yahoo.jdisc.test.TestDriver;
import com.yahoo.log.LogSetup;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertTrue;


/**
 * @author Simon Thoresen Hult
 * @author gjoranv
 */
public class LogFrameworksIntegrationTest {
    private static final String VESPA_LOG_TARGET = "vespa.log.target";

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void requireThatAllSupportedLogFrameworksAreConfigured() throws Exception {
        File logFile = tempFolder.newFile();
        try {
            System.setProperty(VESPA_LOG_TARGET, "file:" + logFile.getAbsolutePath());
            TestDriver driver = TestDriver.newApplicationBundleInstance("app-h-log.jar", false);

            List<String> logLines = IOUtils.getLines(logFile.getAbsolutePath());
            assertLogFileContains("[jdk14] hello world", logLines);
            assertLogFileContains("[slf4j] hello world", logLines);
            assertLogFileContains("[log4j] hello world", logLines);
            assertLogFileContains("[jcl] hello world", logLines);

            assertTrue(driver.close());
        } finally {
            System.clearProperty(VESPA_LOG_TARGET);
            // Triggers a warning that can be ignored. Necessary to reset the log target for later tests.
            LogSetup.initVespaLogging("jdisc_core_test");
        }
    }

    private static void assertLogFileContains(String expected, List<String> logLines) {
        for (var line : logLines) {
            if (line.contains(expected)) return;
        }
        Assert.fail("Log did not contain : " + expected);
    }

}
