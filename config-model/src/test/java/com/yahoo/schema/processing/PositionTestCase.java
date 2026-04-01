// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.PositionDataType;
import com.yahoo.schema.Schema;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.document.Attribute;
import com.yahoo.schema.document.FieldSet;
import com.yahoo.schema.document.MatchType;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryTransform;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test Position processor.
 *
 * @author hmusum
 */
public class PositionTestCase {

    private static Schema buildPositionSchema(String fieldType, String attributeBlock) throws ParseException {
        return ApplicationBuilder.createFromString(
                "search test {\n" +
                "  document test {\n" +
                "    field pos type " + fieldType + " {\n" +
                "      indexing: attribute | summary\n" +
                "      " + attributeBlock + "\n" +
                "    }\n" +
                "  }\n" +
                "}\n").getSchema();
    }

    private static void assertNoStrayPositionAttributes(Schema schema, String fieldName) {
        assertNull(schema.getAttribute(fieldName));
        assertNull(schema.getAttribute(fieldName + ".x"));
        assertNull(schema.getAttribute(fieldName + ".y"));
    }

    @Test
    void requireThatPositionFastSearchIsAllowed() throws Exception {
        Schema schema = buildPositionSchema("position", "attribute: fast-search");
        assertNoStrayPositionAttributes(schema, "pos");
        Attribute zcurve = schema.getAttribute(PositionDataType.getZCurveFieldName("pos"));
        assertNotNull(zcurve);
        assertTrue(zcurve.isFastSearch());
    }

    @Test
    void requireThatPositionFastAccessIsForwarded() throws Exception {
        Schema schema = buildPositionSchema("position", "attribute { fast-access }");
        assertNoStrayPositionAttributes(schema, "pos");
        Attribute zcurve = schema.getAttribute(PositionDataType.getZCurveFieldName("pos"));
        assertNotNull(zcurve);
        assertTrue(zcurve.isFastAccess());
        // Verify that the temporary attribute did not trigger implicit WORD matching
        assertNotEquals(MatchType.WORD, schema.getConcreteField("pos").getMatching().getType());
    }

    @Test
    void requireThatPositionPagedIsForwarded() throws Exception {
        Schema schema = buildPositionSchema("position", "attribute { paged }");
        assertNoStrayPositionAttributes(schema, "pos");
        Attribute zcurve = schema.getAttribute(PositionDataType.getZCurveFieldName("pos"));
        assertNotNull(zcurve);
        assertTrue(zcurve.isPaged());
    }

    @Test
    void requireThatPositionAllSettingsAreForwarded() throws Exception {
        Schema schema = buildPositionSchema("position", "attribute { fast-search\n fast-access\n paged }");
        assertNoStrayPositionAttributes(schema, "pos");
        Attribute zcurve = schema.getAttribute(PositionDataType.getZCurveFieldName("pos"));
        assertNotNull(zcurve);
        assertTrue(zcurve.isFastSearch());
        assertTrue(zcurve.isFastAccess());
        assertTrue(zcurve.isPaged());
    }

    @Test
    void requireThatPositionEmptyAttributeIsAllowed() throws Exception {
        Schema schema = buildPositionSchema("position", "attribute { }");
        assertNoStrayPositionAttributes(schema, "pos");
        Attribute zcurve = schema.getAttribute(PositionDataType.getZCurveFieldName("pos"));
        assertNotNull(zcurve);
        assertTrue(zcurve.isFastSearch());
    }

    @Test
    void requireThatPositionArrayFastAccessIsForwarded() throws Exception {
        Schema schema = buildPositionSchema("array<position>", "attribute { fast-access }");
        assertNoStrayPositionAttributes(schema, "pos");
        Attribute zcurve = schema.getAttribute(PositionDataType.getZCurveFieldName("pos"));
        assertNotNull(zcurve);
        assertTrue(zcurve.isFastAccess());
        assertEquals(Attribute.CollectionType.ARRAY, zcurve.getCollectionType());
    }

    @Test
    void requireThatPositionFastRankIsRejected() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> buildPositionSchema("position", "attribute { fast-rank }"));
        assertTrue(e.getMessage().contains("position fields only support 'fast-search', 'fast-access', and 'paged' attribute settings"));
    }

    @Test
    void requireThatPositionMutableIsRejected() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> buildPositionSchema("position", "attribute { mutable }"));
        assertTrue(e.getMessage().contains("position fields only support 'fast-search', 'fast-access', and 'paged' attribute settings"));
    }

    @Test
    void requireThatPositionBitVectorIsRejected() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> buildPositionSchema("position", "attribute { enable-only-bit-vector }"));
        assertTrue(e.getMessage().contains("position fields only support 'fast-search', 'fast-access', and 'paged' attribute settings"));
    }

    @Test
    void requireThatPositionSortingIsRejected() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> buildPositionSchema("position", "attribute { sorting { ascending } }"));
        assertTrue(e.getMessage().contains("position fields only support 'fast-search', 'fast-access', and 'paged' attribute settings"));
    }

    @Test
    void requireThatPositionDistanceMetricIsRejected() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> buildPositionSchema("position", "attribute { distance-metric: euclidean }"));
        assertTrue(e.getMessage().contains("position fields only support 'fast-search', 'fast-access', and 'paged' attribute settings"));
    }

    @Test
    void requireThatPositionAliasIsRejected() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> buildPositionSchema("position", "attribute { alias: my_alias }"));
        assertTrue(e.getMessage().contains("position fields only support 'fast-search', 'fast-access', and 'paged' attribute settings"));
    }

    @Test
    void requireThatPositionNamedAttributeIsRejected() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> buildPositionSchema("position", "attribute my_attr { fast-search }"));
        assertTrue(e.getMessage().contains("position fields do not support named attribute"));
    }

    @Test
    void requireThatPositionAttributeSettingsRequireAttributing() {
        var e = assertThrows(IllegalArgumentException.class, () ->
                ApplicationBuilder.createFromString(
                        "search test {\n" +
                        "  document test {\n" +
                        "    field pos type position {\n" +
                        "      indexing: summary\n" +
                        "      attribute: fast-search\n" +
                        "    }\n" +
                        "  }\n" +
                        "}\n"));
        assertTrue(e.getMessage().contains("attribute properties require 'attribute' in the indexing statement"));
    }

    @Test
    void inherited_position_zcurve_field_is_not_added_to_document_fieldset() throws Exception {
        ApplicationBuilder sb = ApplicationBuilder.createFromFiles(List.of(
                "src/test/examples/position_base.sd",
                "src/test/examples/position_inherited.sd"));

        Schema schema = sb.getSchema("position_inherited");
        FieldSet fieldSet = schema.getDocument().getFieldSets().builtInFieldSets().get(DocumentType.DOCUMENT);
        assertFalse(fieldSet.getFieldNames().contains(PositionDataType.getZCurveFieldName("pos")));
    }

    @Test
    void requireThatPositionCanBeAttribute() throws Exception {
        Schema schema = ApplicationBuilder.buildFromFile("src/test/examples/position_attribute.sd");
        assertNull(schema.getAttribute("pos"));
        assertNull(schema.getAttribute("pos.x"));
        assertNull(schema.getAttribute("pos.y"));

        assertPositionAttribute(schema, "pos", Attribute.CollectionType.SINGLE);
        assertPositionSummary(schema, "pos", false);
    }

    @Test
    void requireThatPositionCanNotBeIndex() throws Exception {
        try {
            ApplicationBuilder.buildFromFile("src/test/examples/position_index.sd");
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("For schema 'position_index', field 'pos': Indexing of data type 'position' is not " +
                    "supported, replace 'index' statement with 'attribute'.", e.getMessage());
        }
    }

    @Test
    void requireThatSummaryAloneDoesNotCreateZCurve() throws Exception {
        Schema schema = ApplicationBuilder.buildFromFile("src/test/examples/position_summary.sd");
        assertNull(schema.getAttribute("pos"));
        assertNull(schema.getAttribute("pos.x"));
        assertNull(schema.getAttribute("pos.y"));
        assertNull(schema.getAttribute("pos.zcurve"));

        SummaryField summary = schema.getSummaryField("pos");
        assertNotNull(summary);
        assertEquals(2, summary.getSourceCount());
        Iterator<SummaryField.Source> it = summary.getSources().iterator();
        assertEquals("pos.x", it.next().getName());
        assertEquals("pos.y", it.next().getName());
        assertEquals(SummaryTransform.NONE, summary.getTransform());

        assertNull(schema.getSummaryField("pos_ext.distance"));
    }

    @Test
    void requireThatExtraFieldCanBePositionAttribute() throws Exception {
        Schema schema = ApplicationBuilder.buildFromFile("src/test/examples/position_extra.sd");
        assertNull(schema.getAttribute("pos_ext"));
        assertNull(schema.getAttribute("pos_ext.x"));
        assertNull(schema.getAttribute("pos_ext.y"));

        assertPositionAttribute(schema, "pos_ext", Attribute.CollectionType.SINGLE);
        assertPositionSummary(schema, "pos_ext", false);
    }

    @Test
    void requireThatPositionArrayIsSupported() throws Exception {
        Schema schema = ApplicationBuilder.buildFromFile("src/test/examples/position_array.sd");
        assertNull(schema.getAttribute("pos"));
        assertNull(schema.getAttribute("pos.x"));
        assertNull(schema.getAttribute("pos.y"));

        assertPositionAttribute(schema, "pos", Attribute.CollectionType.ARRAY);
        assertPositionSummary(schema, "pos", true);
    }

    private static void assertPositionAttribute(Schema schema, String fieldName, Attribute.CollectionType type) {
        Attribute attribute = schema.getAttribute(PositionDataType.getZCurveFieldName(fieldName));
        assertNotNull(attribute);
        assertTrue(attribute.isPosition());
        assertEquals(attribute.getCollectionType(), type);
        assertEquals(attribute.getType(), Attribute.Type.LONG);
    }

    private static void assertPositionSummary(Schema schema, String fieldName, boolean isArray) {
        assertSummaryField(schema,
                           fieldName,
                           PositionDataType.getZCurveFieldName(fieldName),
                           (isArray ? DataType.getArray(PositionDataType.INSTANCE) : PositionDataType.INSTANCE),
                           SummaryTransform.GEOPOS);
        assertNull(schema.getSummaryField(AdjustPositionSummaryFields.getDistanceSummaryFieldName(fieldName)));
        assertNull(schema.getSummaryField(AdjustPositionSummaryFields.getPositionSummaryFieldName(fieldName)));
    }

    private static void assertSummaryField(Schema schema, String fieldName, String sourceName, DataType dataType,
                                           SummaryTransform transform)
    {
        SummaryField summary = schema.getSummaryField(fieldName);
        assertNotNull(summary);
        assertEquals(1, summary.getSourceCount());
        assertEquals(sourceName, summary.getSingleSource());
        assertEquals(dataType, summary.getDataType());
        assertEquals(transform, summary.getTransform());
    }
}
