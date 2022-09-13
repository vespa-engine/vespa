// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests exporting
 *
 * @author bratseth
 */
public class ExportingTestCase extends AbstractExportingTestCase {

    @Test
    void testIndexInfoLowerCase() throws IOException, ParseException {
        assertCorrectDeriving("indexinfo_lowercase");
    }

    @Test
    void testPositionArray() throws IOException, ParseException {
        assertCorrectDeriving("position_array",
                new TestProperties().setUseV8GeoPositions(true));
    }

    @Test
    void testPositionAttribute() throws IOException, ParseException {
        assertCorrectDeriving("position_attribute",
                new TestProperties().setUseV8GeoPositions(true));
    }

    @Test
    void testPositionExtra() throws IOException, ParseException {
        assertCorrectDeriving("position_extra",
                new TestProperties().setUseV8GeoPositions(true));
    }

    @Test
    void testPositionNoSummary() throws IOException, ParseException {
        assertCorrectDeriving("position_nosummary",
                new TestProperties().setUseV8GeoPositions(true));
    }

    @Test
    void testPositionSummary() throws IOException, ParseException {
        assertCorrectDeriving("position_summary",
                new TestProperties().setUseV8GeoPositions(true));
    }

    @Test
    void testUriArray() throws IOException, ParseException {
        assertCorrectDeriving("uri_array");
    }

    @Test
    void testUriWSet() throws IOException, ParseException {
        assertCorrectDeriving("uri_wset");
    }

    @Test
    void testMusic() throws IOException, ParseException {
        assertCorrectDeriving("music");
    }

    @Test
    void testComplexPhysicalExporting() throws IOException, ParseException {
        assertCorrectDeriving("complex");
    }

    @Test
    void testAttributePrefetch() throws IOException, ParseException {
        assertCorrectDeriving("attributeprefetch");
    }

    @Test
    void testAdvancedIL() throws IOException, ParseException {
        assertCorrectDeriving("advanced");
    }

    @Test
    void testEmptyDefaultIndex() throws IOException, ParseException {
        assertCorrectDeriving("emptydefault");
    }

    @Test
    void testIndexSwitches() throws IOException, ParseException {
        assertCorrectDeriving("indexswitches");
    }

    @Test
    void testRankTypes() throws IOException, ParseException {
        assertCorrectDeriving("ranktypes");
    }

    @Test
    void testAttributeRank() throws IOException, ParseException {
        assertCorrectDeriving("attributerank");
    }

    @Test
    void testNewRank() throws IOException, ParseException {
        assertCorrectDeriving("newrank");
    }

    @Test
    void testRankingExpression() throws IOException, ParseException {
        assertCorrectDeriving("rankingexpression");
    }

    @Test
    void testAvoidRenamingRankingExpression() throws IOException, ParseException {
        assertCorrectDeriving("renamedfeatures", "foo",
                new TestProperties(),
                new TestableDeployLogger());
    }

    @Test
    void testMlr() throws IOException, ParseException {
        assertCorrectDeriving("mlr");
    }

    @Test
    void testMusic3() throws IOException, ParseException {
        assertCorrectDeriving("music3");
    }

    @Test
    void testIndexSchema() throws IOException, ParseException {
        assertCorrectDeriving("indexschema");
    }

    @Test
    void testIndexinfoFieldsets() throws IOException, ParseException {
        assertCorrectDeriving("indexinfo_fieldsets");
    }

    @Test
    void testStreamingJuniper() throws IOException, ParseException {
        assertCorrectDeriving("streamingjuniper");
    }

    @Test
    void testPredicateAttribute() throws IOException, ParseException {
        assertCorrectDeriving("predicate_attribute");
    }

    @Test
    void testTensor() throws IOException, ParseException {
        assertCorrectDeriving("tensor");
    }

    @Test
    void testTensor2() throws IOException, ParseException {
        String dir = "src/test/derived/tensor2/";
        ApplicationBuilder builder = new ApplicationBuilder();
        builder.addSchemaFile(dir + "first.sd");
        builder.addSchemaFile(dir + "second.sd");
        builder.build(true);
        derive("tensor2", builder, builder.getSchema("second"));
        assertCorrectConfigFiles("tensor2");
    }

    @Test
    void testHnswIndex() throws IOException, ParseException {
        assertCorrectDeriving("hnsw_index");
    }

    @Test
    void testRankProfileInheritance() throws IOException, ParseException {
        assertCorrectDeriving("rankprofileinheritance", "child", new TestableDeployLogger());
    }

    @Test
    void testLanguage() throws IOException, ParseException {
        TestableDeployLogger logger = new TestableDeployLogger();
        assertCorrectDeriving("language", logger);
        assertEquals(0, logger.warnings.size());
    }

    @Test
    void testRankProfileModularity() throws IOException, ParseException {
        assertCorrectDeriving("rankprofilemodularity");
    }

    @Test
    void testStructAndFieldSet() throws IOException, ParseException {
        assertCorrectDeriving("structandfieldset");
    }

    @Test
    void testBoldingAndDynamicSummary() throws IOException, ParseException {
        assertCorrectDeriving("bolding_dynamic_summary");
    }

}
