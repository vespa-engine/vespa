// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.log.LogLevel;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;

import com.yahoo.vespa.config.server.deploy.DeployHandlerLogger;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

/**
 * @author lulf
 * @since 5.1
 */
public class DeployHandlerLoggerTest {
    @Test
    public void test_verbose_logging() throws IOException {
        testLogging(true, ".*time.*level\":\"DEBUG\".*message.*time.*level\":\"SPAM\".*message.*time.*level\":\"FINE\".*message.*time.*level\":\"WARNING\".*message.*");
    }

    @Test
    public void test_normal_logging() throws IOException {
        testLogging(false, ".*\\{\"time.*level\":\"WARNING\".*message.*");
    }

    private void testLogging(boolean verbose, String expectedPattern) throws IOException {
        Slime slime = new Slime();
        Cursor array = slime.setArray();
        DeployLogger logger = new DeployHandlerLogger(array, verbose, new ApplicationId.Builder()
                                                      .tenant("testtenant").applicationName("testapp").build());
        logMessages(logger);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new JsonFormat(true).encode(baos, slime);
        assertTrue(Pattern.matches(expectedPattern, baos.toString()));
    }

    private void logMessages(DeployLogger logger) {
        logger.log(LogLevel.DEBUG, "foobar");
        logger.log(LogLevel.SPAM, "foobar");
        logger.log(LogLevel.FINE, "baz");
        logger.log(LogLevel.WARNING, "baz");
    }
}
