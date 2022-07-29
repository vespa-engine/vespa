// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.schema.Schema;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simon Thoresen Hult
 */
public class ImplicitSummariesTestCase {

    @Test
    void requireThatSummaryFromAttributeDoesNotWarn() throws IOException, ParseException {
        LogHandler log = new LogHandler();
        Logger.getLogger("").addHandler(log);

        Schema schema = ApplicationBuilder.buildFromFile("src/test/examples/implicitsummaries_attribute.sd");
        assertNotNull(schema);
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

    @Test
    void attribute_combiner_transform_is_set_on_array_of_struct_with_only_struct_field_attributes() throws IOException, ParseException {
        Schema schema = ApplicationBuilder.buildFromFile("src/test/derived/array_of_struct_attribute/test.sd");
        assertEquals(SummaryTransform.ATTRIBUTECOMBINER, schema.getSummaryField("elem_array").getTransform());
    }

    @Test
    void attribute_combiner_transform_is_set_on_map_of_struct_with_only_struct_field_attributes() throws IOException, ParseException {
        Schema schema = ApplicationBuilder.buildFromFile("src/test/derived/map_of_struct_attribute/test.sd");
        assertEquals(SummaryTransform.ATTRIBUTECOMBINER, schema.getSummaryField("str_elem_map").getTransform());
    }

    @Test
    void attribute_combiner_transform_is_not_set_when_map_of_struct_has_some_struct_field_attributes() throws IOException, ParseException {
        Schema schema = ApplicationBuilder.buildFromFile("src/test/derived/map_of_struct_attribute/test.sd");
        assertEquals(SummaryTransform.NONE, schema.getSummaryField("int_elem_map").getTransform());
    }
}
