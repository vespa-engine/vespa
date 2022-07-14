// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.schema.*;
import com.yahoo.vespa.config.search.SummarymapConfig;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.document.PositionDataType;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.schema.processing.Processing;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import com.yahoo.vespa.model.container.search.QueryProfiles;
import org.junit.Test;

import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
/**
 * Tests summary map extraction
 *
 * @author  bratseth
 */
public class SummaryMapTestCase extends AbstractSchemaTestCase {
    @Test
    public void testDeriving() throws IOException, ParseException {
        Schema schema = ApplicationBuilder.buildFromFile("src/test/examples/simple.sd");
        SummaryMap summaryMap = new SummaryMap(schema);

        Iterator<FieldResultTransform> transforms = summaryMap.resultTransforms().values().iterator();
        FieldResultTransform transform = transforms.next();
        assertEquals("dyndesc", transform.getFieldName());
        assertEquals(SummaryTransform.DYNAMICTEASER, transform.getTransform());

        transform = transforms.next();
        assertEquals("dynlong", transform.getFieldName());
        assertEquals(SummaryTransform.DYNAMICTEASER, transform.getTransform());

        transform = transforms.next();
        assertEquals("dyndesc2", transform.getFieldName());
        assertEquals(SummaryTransform.DYNAMICTEASER, transform.getTransform());

        transform = transforms.next();
        assertEquals("measurement", transform.getFieldName());
        assertEquals(SummaryTransform.ATTRIBUTE, transform.getTransform());

        transform = transforms.next();
        assertEquals("rankfeatures", transform.getFieldName());
        assertEquals(SummaryTransform.RANKFEATURES, transform.getTransform());

        transform = transforms.next();
        assertEquals("summaryfeatures", transform.getFieldName());
        assertEquals(SummaryTransform.SUMMARYFEATURES, transform.getTransform());

        transform = transforms.next();
        assertEquals("popsiness", transform.getFieldName());
        assertEquals(SummaryTransform.ATTRIBUTE, transform.getTransform());

        transform = transforms.next();
        assertEquals("popularity", transform.getFieldName());
        assertEquals(SummaryTransform.ATTRIBUTE, transform.getTransform());

        transform = transforms.next();
        assertEquals("access", transform.getFieldName());
        assertEquals(SummaryTransform.ATTRIBUTE, transform.getTransform());

        transform = transforms.next();
        assertEquals("documentid", transform.getFieldName());
        assertEquals(SummaryTransform.DOCUMENT_ID, transform.getTransform());

        assertFalse(transforms.hasNext());
    }
    @Test
    public void testPositionDeriving() {
        Schema schema = new Schema("store", MockApplicationPackage.createEmpty());
        SDDocumentType document = new SDDocumentType("store");
        schema.addDocument(document);
        String fieldName = "location";
        SDField field = document.addField(fieldName, PositionDataType.INSTANCE);
        field.parseIndexingScript("{ attribute | summary }");
        new Processing().process(schema, new BaseDeployLogger(), new RankProfileRegistry(), new QueryProfiles(),
                                 true, false, Set.of());
        SummaryMap summaryMap = new SummaryMap(schema);

        Iterator<FieldResultTransform> transforms = summaryMap.resultTransforms().values().iterator();

        FieldResultTransform transform = transforms.next();

        assertEquals(fieldName, transform.getFieldName());
        assertEquals(SummaryTransform.GEOPOS, transform.getTransform());

        transform = transforms.next();
        assertEquals("rankfeatures", transform.getFieldName());
        assertEquals(SummaryTransform.RANKFEATURES, transform.getTransform());

        transform = transforms.next();
        assertEquals("summaryfeatures", transform.getFieldName());
        assertEquals(SummaryTransform.SUMMARYFEATURES, transform.getTransform());

        transform = transforms.next();
        assertEquals("location_zcurve", transform.getFieldName());
        assertEquals(SummaryTransform.ATTRIBUTE,transform.getTransform());

        transform = transforms.next();
        assertEquals("documentid", transform.getFieldName());
        assertEquals(SummaryTransform.DOCUMENT_ID,transform.getTransform());

        assertFalse(transforms.hasNext());

        SummarymapConfig.Builder scb = new SummarymapConfig.Builder();
        summaryMap.getConfig(scb);
        SummarymapConfig c = scb.build();
        
        assertEquals(-1, c.defaultoutputclass());
        assertEquals(c.override().size(), 5);

        assertEquals(c.override(0).field(), fieldName);
        assertEquals(c.override(0).command(), "geopos");
        assertEquals(c.override(0).arguments(), PositionDataType.getZCurveFieldName(fieldName));

        assertEquals(c.override(1).field(), "rankfeatures");
        assertEquals(c.override(1).command(), "rankfeatures");
        assertEquals(c.override(1).arguments(), "");
        
        assertEquals(c.override(2).field(), "summaryfeatures");
        assertEquals(c.override(2).command(), "summaryfeatures");
        assertEquals(c.override(2).arguments(), "");

        assertEquals(c.override(3).field(), "location_zcurve");
        assertEquals(c.override(3).command(), "attribute");
        assertEquals(c.override(3).arguments(), "location_zcurve");
    }

    @Test
    public void testFailOnSummaryFieldSourceCollision() {
        try {
            ApplicationBuilder.buildFromFile("src/test/examples/summaryfieldcollision.sd");
        } catch (Exception e) {
            assertTrue(e.getMessage().matches(".*equally named field.*"));
        }
    }

    @Test
    public void source_field_is_passed_as_argument_in_matched_elements_filter_transforms() throws ParseException {
        assertOverride(joinLines("field my_field type map<string, string> {",
                "  indexing: summary",
                "  summary: matched-elements-only",
                "  struct-field key { indexing: attribute }",
                "}"), "my_field", SummaryTransform.MATCHED_ELEMENTS_FILTER.getName());

        assertOverride(joinLines("field my_field type map<string, string> {",
                "  indexing: summary",
                "  summary: matched-elements-only",
                "  struct-field key { indexing: attribute }",
                "  struct-field value { indexing: attribute }",
                "}"), "my_field", SummaryTransform.MATCHED_ATTRIBUTE_ELEMENTS_FILTER.getName());
    }

    @Test
    public void commands_that_are_dynamic_and_require_the_query() {
        assertTrue(SummaryMap.isDynamicCommand("dynamicteaser"));
        assertTrue(SummaryMap.isDynamicCommand(SummaryTransform.MATCHED_ELEMENTS_FILTER.getName()));
        assertTrue(SummaryMap.isDynamicCommand(SummaryTransform.MATCHED_ATTRIBUTE_ELEMENTS_FILTER.getName()));
        assertFalse(SummaryMap.isDynamicCommand(SummaryTransform.ATTRIBUTE.getName()));
    }

    @Test
    public void documentid_summary_field_has_corresponding_summary_transform() throws ParseException {
        var schema = buildSchema("field foo type string { indexing: summary }",
                joinLines("document-summary bar {",
                        "    summary documentid type string {}",
                        "}"));
        assertOverride(schema, "documentid", SummaryTransform.DOCUMENT_ID.getName(), "");
    }

    @Test
    public void documentid_summary_transform_requires_disk_access() {
        assertFalse(SummaryTransform.DOCUMENT_ID.isInMemory());
    }

    private void assertOverride(String fieldContent, String expFieldName, String expCommand) throws ParseException {
        assertOverride(buildSchema(fieldContent, ""), expFieldName, expCommand, expFieldName);
    }

    private void assertOverride(Schema schema, String expFieldName, String expCommand, String expArguments) throws ParseException {
        var summaryMap = new SummaryMap(schema);
        var cfgBuilder = new SummarymapConfig.Builder();
        summaryMap.getConfig(cfgBuilder);
        var cfg = new SummarymapConfig(cfgBuilder);
        var override = cfg.override(0);
        assertEquals(expFieldName, override.field());
        assertEquals(expCommand, override.command());
        assertEquals(expArguments, override.arguments());
    }

    private Schema buildSchema(String field, String documentSummary) throws ParseException {
        var builder = new ApplicationBuilder(new RankProfileRegistry());
        builder.addSchema(joinLines("search test {",
                                    "  document test {",
                                    field,
                                    "  }",
                                    documentSummary,
                                    "}"));
        builder.build(true);
        return builder.getSchema();
    }

}
