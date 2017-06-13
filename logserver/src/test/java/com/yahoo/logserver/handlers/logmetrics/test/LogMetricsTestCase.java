// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.handlers.logmetrics.test;

import java.util.Map;

import com.yahoo.log.InvalidLogFormatException;
import com.yahoo.log.LogMessage;
import com.yahoo.logserver.handlers.logmetrics.LogMetricsHandler;
import com.yahoo.logserver.handlers.logmetrics.LogMetricsPlugin;
import com.yahoo.plugin.SystemPropertyConfig;

import org.junit.*;

import static org.junit.Assert.*;

/**
 * @author hmusum
 */
public class LogMetricsTestCase {
    // Some of the tests depend upon the number of messages for a
    // host, log level etc. to succeed, so you may have update the
    // tests if you change something in mStrings. config, debug and
    // spam are filtered out and not handled.
    private static final String[] mStrings = {
            "1095159244.095\thostA\t1/2\tservice\tcomponent\tconfig\tpayload1",
            "1095206399.000\thostA\t1/2\tservice\tcomponent\tinfo\tpayload2",
            "1095206400.000\thostA\t1/2\tservice\tcomponent\tinfo\tpayload3",
            "1095206401.000\thostA\t1/2\tservice\tcomponent\tinfo\tpayload4",
            "1095206402.000\thostA\t1/2\tservice\tcomponent\twarning\tpayload5",
            "1095206403.000\thostA\t1/2\tservice\tcomponent\terror\tpayload6",
            "1095206404.000\thostB\t1/2\tservice\tcomponent\tinfo\tpayload7",
            "1095206405.000\thostB\t1/2\tservice\tcomponent\tfatal\tpayload8",
            "1095206406.000\thostB\t1/2\tservice\tcomponent\tdebug\tpayload9",
    };

    private static final LogMessage[] msg = new LogMessage[mStrings.length];

    static {
        try {
            for (int i = 0; i < mStrings.length; i++) {
                msg[i] = LogMessage.parseNativeFormat(mStrings[i]);
            }
        } catch (InvalidLogFormatException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Log some messages to the handler and verify that they are
     * counted by the handler and that the count for each level is
     * correct.
     */
    @Test
    public void testLevelCountTotal() throws java.io.IOException, InvalidLogFormatException {
        LogMetricsHandler a = new LogMetricsHandler();

        for (LogMessage aMsg : msg) {
            a.handle(aMsg);
        }

        long count = a.getMetricsCount();
        a.close();
        // Not all messages are processes (debug and spam)
        assertEquals(count, 7);
    }


    /**
     * Log some messages to the handler and verify that they are
     * counted by the handler and that the count for each level is
     * correct (the count for each host is summed into one count for
     * each level).
     */
    @Test
    public void testLevelCountAggregated() throws java.io.IOException, InvalidLogFormatException {
        LogMetricsHandler a = new LogMetricsHandler();

        for (LogMessage aMsg : msg) {
            a.handle(aMsg);
        }

        Map<String, Long> levelCount = a.getMetricsPerLevel();
        assertEquals(levelCount.entrySet().size(), 5);
        for (Map.Entry<String, Long> entry : levelCount.entrySet()) {
            String key = entry.getKey();
            if (key.equals("config")) {
                assertEquals(entry.getValue(), new Long(1));
            } else if (key.equals("info")) {
                assertEquals(entry.getValue(), new Long(4));
            } else if (key.equals("warning")) {
                assertEquals(entry.getValue(), new Long(1));
            } else if (key.equals("severe")) {
                assertEquals(entry.getValue(), new Long(0));
            } else if (key.equals("error")) {
                assertEquals(entry.getValue(), new Long(1));
            } else if (key.equals("fatal")) {
                assertEquals(entry.getValue(), new Long(1));
            } else if (key.equals("debug")) {
                assertEquals(entry.getValue(), new Long(0));  // always 0
            }
        }
        a.close();
    }

    @Test
    public void testLogMetricsPlugin() {
        LogMetricsPlugin lp = new LogMetricsPlugin();
        try {
            lp.shutdownPlugin();
            fail("Shutdown before init didn't throw.");
        } catch (Exception e) {
        }
        lp.initPlugin(new SystemPropertyConfig("test"));
        try {
            lp.initPlugin(new SystemPropertyConfig("test"));
            fail("Multiple init didn't throw.");
        } catch (Exception e) {
        }
        lp.shutdownPlugin();
    }

}
