// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
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

    @Test
    public void attribute_combiner_transform_is_set_on_array_of_struct_with_only_struct_field_attributes() throws IOException, ParseException {
        Search search = SearchBuilder.buildFromFile("src/test/derived/array_of_struct_attribute/test.sd");
        assertEquals(SummaryTransform.ATTRIBUTECOMBINER, search.getSummaryField("elem_array").getTransform());
    }

    @Test
    public void attribute_combiner_transform_is_set_on_map_of_struct_with_only_struct_field_attributes() throws IOException, ParseException {
        Search search = SearchBuilder.buildFromFile("src/test/derived/map_of_struct_attribute/test.sd");
        assertEquals(SummaryTransform.ATTRIBUTECOMBINER, search.getSummaryField("str_elem_map").getTransform());
    }

    @Test
    public void attribute_combiner_transform_is_not_set_when_map_of_struct_has_some_struct_field_attributes() throws IOException, ParseException {
        Search search = SearchBuilder.buildFromFile("src/test/derived/map_of_struct_attribute/test.sd");
        assertEquals(SummaryTransform.NONE, search.getSummaryField("int_elem_map").getTransform());
    }
}
