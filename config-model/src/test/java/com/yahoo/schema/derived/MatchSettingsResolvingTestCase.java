// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * @author arnej
 */
public class MatchSettingsResolvingTestCase extends AbstractExportingTestCase {

    @Test
    void testSimpleDefaults() throws IOException, ParseException {
        assertCorrectDeriving("matchsettings_simple_def", new TestProperties());
    }

    @Test
    void testSimpleWithStructSettings() throws IOException, ParseException {
        assertCorrectDeriving("matchsettings_simple_wss",
                new TestProperties());
    }

    @Test
    void testSimpleWithFieldSettings() throws IOException, ParseException {
        assertCorrectDeriving("matchsettings_simple_wfs", new TestProperties());
    }

    @Test
    void testSimpleStructAndFieldSettings() throws IOException, ParseException {
        assertCorrectDeriving("matchsettings_simple_wss_wfs", new TestProperties());
    }

    @Test
    void testMapDefaults() throws IOException, ParseException {
        assertCorrectDeriving("matchsettings_map_def", new TestProperties());
    }

    @Test
    void testMapWithStructSettings() throws IOException, ParseException {
        assertCorrectDeriving("matchsettings_map_wss", new TestProperties());
    }

    @Test
    void testMapWithFieldSettings() throws IOException, ParseException {
        assertCorrectDeriving("matchsettings_map_wfs", new TestProperties());
    }

    @Test
    void testMapAfter() throws IOException, ParseException {
        assertCorrectDeriving("matchsettings_map_after", new TestProperties());
    }


    @Test
    void testMapInStruct() throws IOException, ParseException {
        assertCorrectDeriving("matchsettings_map_in_struct", new TestProperties());
    }

    
}
