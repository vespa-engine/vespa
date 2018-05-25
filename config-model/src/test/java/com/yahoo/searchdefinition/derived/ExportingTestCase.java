// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;

/**
 * Tests exporting
 *
 * @author bratseth
 */
public class ExportingTestCase extends AbstractExportingTestCase {

    @Test
    public void testIndexInfoLowerCase() throws IOException, ParseException {
        assertCorrectDeriving("indexinfo_lowercase");
    }

    @Test
    public void testPositionArray() throws IOException, ParseException {
        assertCorrectDeriving("position_array");
    }

    @Test
    public void testPositionAttribute() throws IOException, ParseException {
        assertCorrectDeriving("position_attribute");
    }

    @Test
    public void testPositionExtra() throws IOException, ParseException {
        assertCorrectDeriving("position_extra");
    }

    @Test
    public void testPositionNoSummary() throws IOException, ParseException {
        assertCorrectDeriving("position_nosummary");
    }

    @Test
    public void testPositionSummary() throws IOException, ParseException {
        assertCorrectDeriving("position_summary");
    }

    @Test
    public void testUriArray() throws IOException, ParseException {
        assertCorrectDeriving("uri_array");
    }

    @Test
    public void testUriWSet() throws IOException, ParseException {
        assertCorrectDeriving("uri_wset");
    }

    @Test
    public void testMusic() throws IOException, ParseException {
        assertCorrectDeriving("music");
    }

    @Test
    public void testComplexPhysicalExporting() throws IOException, ParseException {
        assertCorrectDeriving("complex");
    }

    @Test
    public void testAttributePrefetch() throws IOException, ParseException {
        assertCorrectDeriving("attributeprefetch");
    }

    @Test
    public void testAdvancedIL() throws IOException, ParseException {
        assertCorrectDeriving("advanced");
    }

    @Test
    public void testEmptyDefaultIndex() throws IOException, ParseException {
        assertCorrectDeriving("emptydefault");
    }

    @Test
    public void testIndexSwitches() throws IOException, ParseException {
        assertCorrectDeriving("indexswitches");
    }

    @Test
    public void testRankTypes() throws IOException, ParseException {
        assertCorrectDeriving("ranktypes");
    }

    @Test
    public void testAttributeRank() throws IOException, ParseException {
        assertCorrectDeriving("attributerank");
    }

    @Test
    public void testNewRank() throws IOException, ParseException {
        assertCorrectDeriving("newrank");
    }

    @Test
    public void testRankExpression() throws IOException, ParseException {
        assertCorrectDeriving("rankexpression");
    }

    @Test
    public void testMlr() throws IOException, ParseException {
        assertCorrectDeriving("mlr");
    }

    @Test
    public void testMusic3() throws IOException, ParseException {
        assertCorrectDeriving("music3");
    }

    @Test
    public void testIndexSchema() throws IOException, ParseException {
        assertCorrectDeriving("indexschema");
    }

    @Test
    public void testIndexinfoFieldsets() throws IOException, ParseException {
        assertCorrectDeriving("indexinfo_fieldsets");
    }

    @Test
    public void testStreamingJuniper() throws IOException, ParseException {
        assertCorrectDeriving("streamingjuniper");
    }

    @Test
    public void testPredicateAttribute() throws IOException, ParseException {
        assertCorrectDeriving("predicate_attribute");
    }

    @Test
    public void testTensor() throws IOException, ParseException {
        assertCorrectDeriving("tensor");
    }

    @Test
    public void testArrayOfStructAttribute() throws IOException, ParseException {
        assertCorrectDeriving("array_of_struct_attribute");
    }

}
