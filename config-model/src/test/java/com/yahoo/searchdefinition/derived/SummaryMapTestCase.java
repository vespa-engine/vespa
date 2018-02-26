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

import static org.junit.Assert.assertEquals;
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
        SummaryMap summaryMap=new SummaryMap(search, new Summaries(search, new BaseDeployLogger()));

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

        assertTrue(!transforms.hasNext());
    }
    @Test
    public void testPositionDeriving() throws IOException, ParseException {
        Search search = new Search("store", null);
        SDDocumentType document = new SDDocumentType("store");
        search.addDocument(document);
        String fieldName = "location";
        SDField field = document.addField(fieldName, PositionDataType.INSTANCE);
        field.parseIndexingScript("{ attribute | summary }");
        Processing.process(search, new BaseDeployLogger(), new RankProfileRegistry(), new QueryProfiles(), true);
        SummaryMap summaryMap = new SummaryMap(search, new Summaries(search, new BaseDeployLogger()));

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

        assertTrue(!transforms.hasNext());

        SummarymapConfig.Builder scb = new SummarymapConfig.Builder();
        summaryMap.getConfig(scb);
        SummarymapConfig c = new SummarymapConfig(scb);
        
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
    public void testFailOnSummaryFieldSourceCollision() throws IOException, ParseException {
        try {
            Search search = SearchBuilder.buildFromFile("src/test/examples/summaryfieldcollision.sd");
        } catch (Exception e) {
            assertTrue(e.getMessage().matches(".*equally named field.*"));
        }
    }

}
