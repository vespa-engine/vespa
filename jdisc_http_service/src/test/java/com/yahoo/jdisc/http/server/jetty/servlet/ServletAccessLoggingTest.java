// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty.servlet;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.container.logging.AccessLogEntry;
import com.yahoo.jdisc.http.server.jetty.TestDriver;
import com.yahoo.jdisc.http.server.jetty.TestDrivers;
import org.junit.Test;
import org.mockito.verification.VerificationMode;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * @author bakksjo
 */
public class ServletAccessLoggingTest extends ServletTestBase {
    private static final long MAX_LOG_WAIT_TIME_MILLIS = TimeUnit.SECONDS.toMillis(60);

    @Test
    public void accessLogIsInvokedForNonJDiscServlet() throws Exception {
        final AccessLog accessLog = mock(AccessLog.class);
        final TestDriver testDriver = newTestDriver(accessLog);
        httpGet(testDriver, TestServlet.PATH).execute();
        verifyCallsLog(accessLog, timeout(MAX_LOG_WAIT_TIME_MILLIS).times(1));
    }

    @Test
    public void accessLogIsInvokedForJDiscServlet() throws Exception {
        final AccessLog accessLog = mock(AccessLog.class);
        final TestDriver testDriver = newTestDriver(accessLog);
        testDriver.client().newGet("/status.html").execute();
        verifyCallsLog(accessLog, timeout(MAX_LOG_WAIT_TIME_MILLIS).times(1));
    }

    private void verifyCallsLog(final AccessLog accessLog, final VerificationMode verificationMode) {
        verify(accessLog, verificationMode).log(any(AccessLogEntry.class));
    }

    private TestDriver newTestDriver(final AccessLog accessLog) throws IOException {
        return TestDrivers.newInstance(dummyRequestHandler, bindings(accessLog));
    }

    private Module bindings(final AccessLog accessLog) {
        return Modules.combine(
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(AccessLog.class).toInstance(accessLog);
                    }
                },
                guiceModule());
    }
}
