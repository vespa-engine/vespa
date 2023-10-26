// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
