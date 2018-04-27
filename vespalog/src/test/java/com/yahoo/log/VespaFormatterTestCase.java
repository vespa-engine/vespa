// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.junit.Test;
import org.junit.Before;
import org.junit.Ignore;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.is;

/**
 * @author  Bjorn Borud
 */
// TODO: Remove annotation and replace setMillis with setInstant when we don't support Java 8 anymore.
@SuppressWarnings("deprecation")
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
        hostname = Util.getHostName();
        pid = Util.getPID();

        testRecord1 = new LogRecord(Level.INFO, "this is a test");
        testRecord1.setMillis(1098709021843L);
        testRecord1.setThreadID(123);

        expected1 = "1098709021.843\t"
            + hostname + "\t"
            + pid
            + "/123" + "\t"
            + "serviceName\t"
            + "tst\t"
            + "info\t"
            + "this is a test\n";

        expected2 = "1098709021.843\t"
            + hostname + "\t"
            + pid
            + "/123" + "\t"
            + "serviceName\t"
            + "-\t"
            + "info\t"
            + "this is a test\n";


        testRecord2 = new LogRecord(Level.INFO, "this is a test");
        testRecord2.setMillis(1098709021843L);
        testRecord2.setThreadID(123);
        testRecord2.setLoggerName("org.foo");

        expected3 = "1098709021.843\t"
            + hostname + "\t"
            + pid
            + "/123" + "\t"
            + "serviceName\t"
            + ".org.foo\t"
            + "info\t"
            + "this is a test\n";

        expected4 = "1098709021.843\t"
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
        testRecord.setMillis(1098709021843L);
        testRecord.setThreadID(123);
        testRecord.setLoggerName("org.foo");
        Object[] params = { "a small", "message" };
        testRecord.setParameters(params);

        String expected = "1098709021.843\t"
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

    /**
     * This test doesn't really do that much.  It is just here
     * to ensure this doesn't crash.  XXX TODO: make this test
     * actually test something more than just the non-generation
     * of runtime errors. -bb
     */
    @Test
    public void testExceptionFormatting () {
        StringBuilder sb = new StringBuilder(128);
        Exception e = new Exception("testing", new Exception("nested"));
        VespaFormat.formatException(e, sb);
    }


    @Test
    public void testGeneralFormat() {
        String[] expected = new String[] {
                "54.321",
                "hostname",
                "26019/UnitTest-Thread-37",
                "UnitTestRunner",
                "com.UnitTest",
                "INFO",
                "Just check it looks OK\\nmsg=\"boom\"\\nname=\"java.lang.Throwable\"\\nstack=\"\\n" + this.getClass().getName() // Clover rewrites class names, get the current one to avoid test failure
        };
        String formatted = VespaFormat.format("INFO",
                "UnitTest", "com", 54321L,
                "UnitTest-Thread-37", "UnitTestRunner",
                "Just check it looks OK",
                new Throwable("boom"));
        String[] split = formatted.split("\t");
        assertEquals(expected[0], split[0]);
        assertEquals(expected[2].split("/")[1], split[2].split("/")[1]);
        assertEquals(expected[3], split[3]);
        assertEquals(expected[4], split[4]);
        assertEquals(expected[5], split[5]);
        assertEquals(expected[6], split[6].substring(0, expected[6].length()));
        assertEquals(expected.length, split.length);
    }
}
