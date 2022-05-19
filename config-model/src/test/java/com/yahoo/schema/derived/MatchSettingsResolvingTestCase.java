// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.schema.parser.ParseException;
import org.junit.Test;

import java.io.IOException;

/**
 * @author arnej
 */
public class MatchSettingsResolvingTestCase extends AbstractExportingTestCase {

    @Test
    public void testSimpleDefaults() throws IOException, ParseException {
        assertCorrectDeriving("matchsettings_simple_def", new TestProperties());
    }

    @Test
    public void testSimpleWithStructSettings() throws IOException, ParseException {
        assertCorrectDeriving("matchsettings_simple_wss",
                              new TestProperties());
    }

    @Test
    public void testSimpleWithFieldSettings() throws IOException, ParseException {
        assertCorrectDeriving("matchsettings_simple_wfs", new TestProperties());
    }

    @Test
    public void testSimpleStructAndFieldSettings() throws IOException, ParseException {
        assertCorrectDeriving("matchsettings_simple_wss_wfs", new TestProperties());
    }

    @Test
    public void testMapDefaults() throws IOException, ParseException {
        assertCorrectDeriving("matchsettings_map_def", new TestProperties());
    }

    @Test
    public void testMapWithStructSettings() throws IOException, ParseException {
        assertCorrectDeriving("matchsettings_map_wss", new TestProperties());
    }

    @Test
    public void testMapWithFieldSettings() throws IOException, ParseException {
        assertCorrectDeriving("matchsettings_map_wfs", new TestProperties());
    }

    @Test
    public void testMapAfter() throws IOException, ParseException {
        assertCorrectDeriving("matchsettings_map_after", new TestProperties());
    }


    @Test
    public void testMapInStruct() throws IOException, ParseException {
        assertCorrectDeriving("matchsettings_map_in_struct", new TestProperties());
    }

    
}
