// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
public class ImplicitSummariesTestCase {

    @Test
    public void requireThatSummaryFromAttributeDoesNotWarn() throws IOException, ParseException {
        LogHandler log = new LogHandler();
        Logger.getLogger("").addHandler(log);

        Search search = SearchBuilder.buildFromFile("src/test/examples/implicitsummaries_attribute.sd");
        assertNotNull(search);
        assertTrue(log.records.isEmpty());
    }

    private static class LogHandler extends Handler {

        final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            if (record.getLevel() == Level.WARNING ||
                record.getLevel() == Level.SEVERE)
            {
                records.add(record);
            }
        }

        @Override
        public void flush() {

        }

        @Override
        public void close() throws SecurityException {

        }
    }
}
