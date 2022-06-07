package com.yahoo.log;


import java.util.logging.LogRecord;

/**
 * @author gjoranv
 */
public class TestUtil {

    public static String formatWithVespaFormatter(LogRecord record) {
        return new VespaFormatter().format(record);
    }

}
