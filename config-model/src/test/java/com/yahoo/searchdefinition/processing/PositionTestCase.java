// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.PositionDataType;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.searchdefinition.document.FieldSet;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryTransform;

import org.junit.Test;

import java.util.Arrays;
import java.util.Iterator;

import static org.junit.Assert.*;

/**
 * Test Position processor.
 *
 * @author hmusum
 */
public class PositionTestCase {

    @Test
    public void inherited_position_zcurve_field_is_not_added_to_document_fieldset() throws Exception {
        SearchBuilder sb = SearchBuilder.createFromFiles(Arrays.asList(
                "src/test/examples/position_base.sd",
                "src/test/examples/position_inherited.sd"));

        Search search = sb.getSearch("position_inherited");
        FieldSet fieldSet = search.getDocument().getFieldSets().builtInFieldSets().get(DocumentType.DOCUMENT);
        assertFalse(fieldSet.getFieldNames().contains(PositionDataType.getZCurveFieldName("pos")));
    }

    @Test
    public void requireThatPositionCanBeAttribute() throws Exception {
        Search search = SearchBuilder.buildFromFile("src/test/examples/position_attribute.sd");
        assertNull(search.getAttribute("pos"));
        assertNull(search.getAttribute("pos.x"));
        assertNull(search.getAttribute("pos.y"));

        assertPositionAttribute(search, "pos", Attribute.CollectionType.SINGLE);
        assertPositionSummary(search, "pos", false);
    }

    @Test
    public void requireThatPositionCanNotBeIndex() throws Exception {
        try {
            SearchBuilder.buildFromFile("src/test/examples/position_index.sd");
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("For search 'position_index', field 'pos': Indexing of data type 'position' is not " +
                         "supported, replace 'index' statement with 'attribute'.", e.getMessage());
        }
    }

    @Test
    public void requireThatSummaryAloneDoesNotCreateZCurve() throws Exception {
        Search search = SearchBuilder.buildFromFile("src/test/examples/position_summary.sd");
        assertNull(search.getAttribute("pos"));
        assertNull(search.getAttribute("pos.x"));
        assertNull(search.getAttribute("pos.y"));
        assertNull(search.getAttribute("pos.zcurve"));

        SummaryField summary = search.getSummaryField("pos");
        assertNotNull(summary);
        assertEquals(2, summary.getSourceCount());
        Iterator<SummaryField.Source> it = summary.getSources().iterator();
        assertEquals("pos.x", it.next().getName());
        assertEquals("pos.y", it.next().getName());
        assertEquals(SummaryTransform.NONE, summary.getTransform());

        assertNull(search.getSummaryField("pos_ext.distance"));
    }

    @Test
    public void requireThatExtraFieldCanBePositionAttribute() throws Exception {
        Search search = SearchBuilder.buildFromFile("src/test/examples/position_extra.sd");
        assertNull(search.getAttribute("pos_ext"));
        assertNull(search.getAttribute("pos_ext.x"));
        assertNull(search.getAttribute("pos_ext.y"));

        assertPositionAttribute(search, "pos_ext", Attribute.CollectionType.SINGLE);
        assertPositionSummary(search, "pos_ext", false);
    }

    @Test
    public void requireThatPositionArrayIsSupported() throws Exception {
        Search search = SearchBuilder.buildFromFile("src/test/examples/position_array.sd");
        assertNull(search.getAttribute("pos"));
        assertNull(search.getAttribute("pos.x"));
        assertNull(search.getAttribute("pos.y"));

        assertPositionAttribute(search, "pos", Attribute.CollectionType.ARRAY);
        assertPositionSummary(search, "pos", true);
    }

    private static void assertPositionAttribute(Search search, String fieldName, Attribute.CollectionType type) {
        Attribute attribute = search.getAttribute(PositionDataType.getZCurveFieldName(fieldName));
        assertNotNull(attribute);
        assertTrue(attribute.isPosition());
        assertEquals(attribute.getCollectionType(), type);
        assertEquals(attribute.getType(), Attribute.Type.LONG);
    }

    private static void assertPositionSummary(Search search, String fieldName, boolean isArray) {
        assertSummaryField(search,
                           fieldName,
                           PositionDataType.getZCurveFieldName(fieldName),
                           (isArray ? DataType.getArray(PositionDataType.INSTANCE) : PositionDataType.INSTANCE),
                           SummaryTransform.GEOPOS);
        assertSummaryField(search,
                           PositionDataType.getDistanceSummaryFieldName(fieldName),
                           PositionDataType.getZCurveFieldName(fieldName),
                           DataType.INT,
                           SummaryTransform.DISTANCE);
        assertSummaryField(search,
                           PositionDataType.getPositionSummaryFieldName(fieldName),
                           PositionDataType.getZCurveFieldName(fieldName),
                           DataType.getArray(DataType.STRING),
                           SummaryTransform.POSITIONS);
    }

    private static void assertSummaryField(Search search, String fieldName, String sourceName, DataType dataType,
                                           SummaryTransform transform)
    {
        SummaryField summary = search.getSummaryField(fieldName);
        assertNotNull(summary);
        assertEquals(1, summary.getSourceCount());
        assertEquals(sourceName, summary.getSingleSource());
        assertEquals(dataType, summary.getDataType());
        assertEquals(transform, summary.getTransform());
    }
}
