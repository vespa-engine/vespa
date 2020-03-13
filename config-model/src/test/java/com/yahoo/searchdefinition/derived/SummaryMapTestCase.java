// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.searchdefinition.*;
import com.yahoo.vespa.config.search.SummarymapConfig;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.document.PositionDataType;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.searchdefinition.processing.Processing;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import com.yahoo.vespa.model.container.search.QueryProfiles;
import org.junit.Test;

import java.io.IOException;
import java.util.Iterator;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
/**
 * Tests summary map extraction
 *
 * @author  bratseth
 */
public class SummaryMapTestCase extends SearchDefinitionTestCase {
    @Test
    public void testDeriving() throws IOException, ParseException {
        Search search = SearchBuilder.buildFromFile("src/test/examples/simple.sd");
        SummaryMap summaryMap=new SummaryMap(search);

        Iterator transforms=summaryMap.resultTransformIterator();
        FieldResultTransform transform = (FieldResultTransform)transforms.next();
        assertEquals("dyndesc", transform.getFieldName());
        assertEquals(SummaryTransform.DYNAMICTEASER,transform.getTransform());

        transform = (FieldResultTransform)transforms.next();
        assertEquals("dynlong", transform.getFieldName());
        assertEquals(SummaryTransform.DYNAMICTEASER,transform.getTransform());

        transform = (FieldResultTransform)transforms.next();
        assertEquals("dyndesc2", transform.getFieldName());
        assertEquals(SummaryTransform.DYNAMICTEASER,transform.getTransform());

        transform = (FieldResultTransform)transforms.next();
        assertEquals("measurement", transform.getFieldName());
        assertEquals(SummaryTransform.ATTRIBUTE,transform.getTransform());

        transform = (FieldResultTransform)transforms.next();
        assertEquals("rankfeatures", transform.getFieldName());
        assertEquals(SummaryTransform.RANKFEATURES, transform.getTransform());

        transform = (FieldResultTransform)transforms.next();
        assertEquals("summaryfeatures", transform.getFieldName());
        assertEquals(SummaryTransform.SUMMARYFEATURES, transform.getTransform());

        transform = (FieldResultTransform)transforms.next();
        assertEquals("popsiness", transform.getFieldName());
        assertEquals(SummaryTransform.ATTRIBUTE,transform.getTransform());

        transform = (FieldResultTransform)transforms.next();
        assertEquals("popularity", transform.getFieldName());
        assertEquals(SummaryTransform.ATTRIBUTE,transform.getTransform());

        transform = (FieldResultTransform)transforms.next();
        assertEquals("access", transform.getFieldName());
        assertEquals(SummaryTransform.ATTRIBUTE,transform.getTransform());

        assertFalse(transforms.hasNext());
    }
    @Test
    public void testPositionDeriving() {
        Search search = new Search("store", null);
        SDDocumentType document = new SDDocumentType("store");
        search.addDocument(document);
        String fieldName = "location";
        SDField field = document.addField(fieldName, PositionDataType.INSTANCE);
        field.parseIndexingScript("{ attribute | summary }");
        new Processing().process(search, new BaseDeployLogger(), new RankProfileRegistry(), new QueryProfiles(), true, false);
        SummaryMap summaryMap = new SummaryMap(search);

        Iterator transforms = summaryMap.resultTransformIterator();

        FieldResultTransform transform = (FieldResultTransform)transforms.next();

        assertEquals(fieldName, transform.getFieldName());
        assertEquals(SummaryTransform.GEOPOS, transform.getTransform());

        transform = (FieldResultTransform)transforms.next();
        assertEquals(PositionDataType.getPositionSummaryFieldName(fieldName), transform.getFieldName());
        assertEquals(SummaryTransform.POSITIONS, transform.getTransform());

        transform = (FieldResultTransform)transforms.next();
        assertEquals(PositionDataType.getDistanceSummaryFieldName(fieldName), transform.getFieldName());
        assertEquals(SummaryTransform.DISTANCE,transform.getTransform());

        transform = (FieldResultTransform)transforms.next();
        assertEquals("rankfeatures", transform.getFieldName());
        assertEquals(SummaryTransform.RANKFEATURES, transform.getTransform());

        transform = (FieldResultTransform)transforms.next();
        assertEquals("summaryfeatures", transform.getFieldName());
        assertEquals(SummaryTransform.SUMMARYFEATURES, transform.getTransform());

        transform = (FieldResultTransform)transforms.next();
        assertEquals("location_zcurve", transform.getFieldName());
        assertEquals(SummaryTransform.ATTRIBUTE,transform.getTransform());

        assertFalse(transforms.hasNext());

        SummarymapConfig.Builder scb = new SummarymapConfig.Builder();
        summaryMap.getConfig(scb);
        SummarymapConfig c = scb.build();
        
        assertEquals(-1, c.defaultoutputclass());
        assertEquals(c.override().size(), 6);

        assertEquals(c.override(0).field(), fieldName);
        assertEquals(c.override(0).command(), "geopos");
        assertEquals(c.override(0).arguments(), PositionDataType.getZCurveFieldName(fieldName));

        assertEquals(c.override(1).field(), PositionDataType.getPositionSummaryFieldName(fieldName));
        assertEquals(c.override(1).command(), "positions");
        assertEquals(c.override(1).arguments(), PositionDataType.getZCurveFieldName(fieldName));

        assertEquals(c.override(2).field(), PositionDataType.getDistanceSummaryFieldName(fieldName));
        assertEquals(c.override(2).command(), "absdist");
        assertEquals(c.override(2).arguments(), PositionDataType.getZCurveFieldName(fieldName));

        assertEquals(c.override(3).field(), "rankfeatures");
        assertEquals(c.override(3).command(), "rankfeatures");
        assertEquals(c.override(3).arguments(), "");
        
        assertEquals(c.override(4).field(), "summaryfeatures");
        assertEquals(c.override(4).command(), "summaryfeatures");
        assertEquals(c.override(4).arguments(), "");

        assertEquals(c.override(5).field(), "location_zcurve");
        assertEquals(c.override(5).command(), "attribute");
        assertEquals(c.override(5).arguments(), "location_zcurve");
    }

    @Test
    public void testFailOnSummaryFieldSourceCollision() {
        try {
            SearchBuilder.buildFromFile("src/test/examples/summaryfieldcollision.sd");
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

    private void assertOverride(String fieldContent, String expFieldName, String expCommand) throws ParseException {
        var summaryMap = new SummaryMap(buildSearch(fieldContent));
        var cfgBuilder = new SummarymapConfig.Builder();
        summaryMap.getConfig(cfgBuilder);
        var cfg = new SummarymapConfig(cfgBuilder);
        var override = cfg.override(0);
        assertEquals(expFieldName, override.field());
        assertEquals(expCommand, override.command());
        assertEquals(expFieldName, override.arguments());
    }

    private Search buildSearch(String field) throws ParseException {
        var builder = new SearchBuilder(new RankProfileRegistry());
        builder.importString(joinLines("search test {",
                "  document test {",
                field,
                "  }",
                "}"));
        builder.build();
        return builder.getSearch();
    }

}
