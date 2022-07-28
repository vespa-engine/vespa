// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Check basic error message formatting. Do note these tests are sensitive to
 * the line numbering in this file. (And that's a feature, not a bug.)
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class ExceptionWrapperTest {

    @Test
    final void requireNoMessageIsOK() {
        final Throwable t = new Throwable();
        final ExceptionWrapper e = new ExceptionWrapper(t);
        final String expected = "Throwable() at com.yahoo.jdisc.http.server.jetty.ExceptionWrapperTest(ExceptionWrapperTest.java:19)";

        assertThat(e.getMessage(), equalTo(expected));
    }

    @Test
    final void requireAllWrappedLevelsShowUp() {
        final Throwable t0 = new Throwable("t0");
        final Throwable t1 = new Throwable("t1", t0);
        final Throwable t2 = new Throwable("t2", t1);
        final ExceptionWrapper e = new ExceptionWrapper(t2);
        final String expected = "Throwable(\"t2\") at com.yahoo.jdisc.http.server.jetty.ExceptionWrapperTest(ExceptionWrapperTest.java:30):"
                + " Throwable(\"t1\") at com.yahoo.jdisc.http.server.jetty.ExceptionWrapperTest(ExceptionWrapperTest.java:29):"
                + " Throwable(\"t0\") at com.yahoo.jdisc.http.server.jetty.ExceptionWrapperTest(ExceptionWrapperTest.java:28)";

        assertThat(e.getMessage(), equalTo(expected));
    }

    @Test
    final void requireMixOfMessageAndNoMessageWorks() {
        final Throwable t0 = new Throwable("t0");
        final Throwable t1 = new Throwable(t0);
        final Throwable t2 = new Throwable("t2", t1);
        final ExceptionWrapper e = new ExceptionWrapper(t2);
        final String expected = "Throwable(\"t2\") at com.yahoo.jdisc.http.server.jetty.ExceptionWrapperTest(ExceptionWrapperTest.java:43):"
                + " Throwable(\"java.lang.Throwable: t0\") at com.yahoo.jdisc.http.server.jetty.ExceptionWrapperTest(ExceptionWrapperTest.java:42):"
                + " Throwable(\"t0\") at com.yahoo.jdisc.http.server.jetty.ExceptionWrapperTest(ExceptionWrapperTest.java:41)";

        assertThat(e.getMessage(), equalTo(expected));
    }
}
