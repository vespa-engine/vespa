// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import com.yahoo.log.impl.LogUtils;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author  Bjorn Borud
 */
@SuppressWarnings({"deprecation", "removal"})
public class VespaFormatterTestCase {

    private String hostname;
    private String pid;
    private String serviceName = "serviceName";
    private String app = "tst";
    private LogRecord testRecord1;
    private LogRecord testRecord2;
    private String expected1;
    private String expected2;
    private String expected3;
    private String expected4;


    @Before
    public void setUp () {
        hostname = LogUtils.getHostName();
        pid = LogUtils.getPID();

        testRecord1 = new LogRecord(Level.INFO, "this is a test");
        testRecord1.setInstant(Instant.ofEpochMilli(1098709021843L));
        testRecord1.setLongThreadID(123L);

        expected1 = "1098709021.843000\t"
            + hostname + "\t"
            + pid
            + "/123" + "\t"
            + "serviceName\t"
            + "tst\t"
            + "info\t"
            + "this is a test\n";

        expected2 = "1098709021.843000\t"
            + hostname + "\t"
            + pid
            + "/123" + "\t"
            + "serviceName\t"
            + "-\t"
            + "info\t"
            + "this is a test\n";


        testRecord2 = new LogRecord(Level.INFO, "this is a test");
        testRecord2.setInstant(Instant.ofEpochMilli(1098709021843L));
        testRecord2.setLongThreadID(123L);
        testRecord2.setLoggerName("org.foo");

        expected3 = "1098709021.843000\t"
            + hostname + "\t"
            + pid
            + "/123" + "\t"
            + "serviceName\t"
            + ".org.foo\t"
            + "info\t"
            + "this is a test\n";

        expected4 = "1098709021.843000\t"
            + hostname + "\t"
            + pid
            + "/123" + "\t"
            + "serviceName\t"
            + "tst.org.foo\t"
            + "info\t"
            + "this is a test\n";
    }

    /**
     * Just make sure that the messages are parsed as expected.
     */
    @Test
    public void testFormatting () {
        VespaFormatter formatter = new VespaFormatter(serviceName, app);
        assertEquals(expected1, formatter.format(testRecord1));
        assertEquals(expected4, formatter.format(testRecord2));

        VespaFormatter fmt2 = new VespaFormatter(serviceName, null);
        assertEquals(expected2, fmt2.format(testRecord1));
        assertEquals(expected3, fmt2.format(testRecord2));
    }


    /**
     * test that {0} etc is replaced properly
     */
    @Test
    public void testTextFormatting () {
        VespaFormatter formatter = new VespaFormatter(serviceName, app);

        LogRecord testRecord = new LogRecord(Level.INFO, "this {1} is {0} test");
        testRecord.setInstant(Instant.ofEpochMilli(1098709021843L));
        testRecord.setLongThreadID(123L);
        testRecord.setLoggerName("org.foo");
        Object[] params = { "a small", "message" };
        testRecord.setParameters(params);

        String expected = "1098709021.843000\t"
                          + hostname + "\t"
                          + pid
                          + "/123" + "\t"
                          + "serviceName\t"
                          + app
                          + ".org.foo\t"
                          + "info\t"
                          + "this message is a small test\n";

        assertEquals(expected, formatter.format(testRecord));
    }

    /**
     * Make sure that the LogMessage parser used in the log server is
     * able to parse these messages too.
     */
    @Test
    public void testParsing () {
        VespaFormatter formatter = new VespaFormatter(serviceName, app);
        LogMessage m;
        try {
            m = LogMessage.parseNativeFormat(expected1.trim());
            assertEquals("this is a test", m.getPayload());

            m = LogMessage.parseNativeFormat(formatter.format(testRecord1).trim());
            assertEquals("this is a test", m.getPayload());

            assertTrue(true);
        }
        catch (InvalidLogFormatException e) {
            System.out.println(e.toString());
            fail();
        }
    }

    /**
     * This method was used for testing the speed of the formatter.
     * We usually do not want to run this in normal testing, it was
     * just added when trying to analyze the abysmal performance of
     * double formatting.
     */
    @Test
    @Ignore
    public void testSpeed () {
        VespaFormatter formatter = new VespaFormatter(serviceName, app);
        int reps = 1000000;
        long start = System.currentTimeMillis();
        for (int i = 0; i < reps; i++) {
            formatter.format(testRecord1);
        }
        long diff = System.currentTimeMillis() - start;
        double dd = (reps / (double) diff);
        System.out.println("Took " + diff + " milliseconds, per ms=" + dd);
    }


    /** test backslash, newline and tab escaping.  one instance of each */
    @Test
    public void testEscaping () {
        String a = "This line contains a \\ backslash and \n newline and \t tab.";
        String b = "This line contains a \\\\ backslash and \\n newline and \\t tab.";
        assertEquals(b, VespaFormat.escape(a));
        assertEquals(a, VespaFormatter.unEscape(b));
    }

    /** test multiple instances of backslash */
    @Test
    public void testMultipleEscaping () {
        String a = "This line contains a \\ backslash and \\ more backslash.";
        String b = "This line contains a \\\\ backslash and \\\\ more backslash.";
        assertEquals(b, VespaFormat.escape(a));
        assertEquals(a, VespaFormatter.unEscape(b));
    }

    @Test
    public void testThrowable () {
        // VespaFormatter formatter = new VespaFormatter(serviceName, app);
        LogRecord r = new LogRecord(Level.INFO, "foo bar");
        r.setThrown(new IllegalStateException("this was fun"));
    }

    /**
     * Got a NullPointerException earlier when trying to format
     * a null message.  Added unit test to make sure this does
     * not happen again.  Makes sure that NullPointerException
     * is not thrown and that the formatted string contains the
     * expected message for saying that there was no log message.
     */
    @Test
    public void testNullMessage () {
        VespaFormatter formatter = new VespaFormatter(serviceName, app);
        LogRecord r = new LogRecord(LogLevel.ERROR, null);
        assertNotNull(r);
        try {
            String s = formatter.format(r);
            assertNotNull(s);
            assertTrue(s.indexOf("(empty)") > 0);
        }
        catch (NullPointerException e) {
            fail("unable to handle null message!");
        }
    }

    @Test
    public void testLowLogLevelExceptionFormatting () {
        VespaFormatter formatter = new VespaFormatter(serviceName, app);
        LogRecord r = new LogRecord(LogLevel.INFO, "meldingen her");
        r.setThrown(new IllegalStateException());
        String result = formatter.format(r);
        assertTrue(formatter.format(r).contains("meldingen her"));
    }

}
