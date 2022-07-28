// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.document.DataType;
import com.yahoo.document.PositionDataType;
import com.yahoo.schema.Schema;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AdjustPositionSummaryFieldsTestCase {

    @Test
    void test_pos_summary() {
        SearchModel model = new SearchModel(false);
        model.addSummaryField("my_pos", PositionDataType.INSTANCE, null, "pos");
        model.resolve();
        model.assertSummaryField("my_pos", PositionDataType.INSTANCE, SummaryTransform.GEOPOS, "pos_zcurve");
        model.assertSummaryField("my_pos.position", DataType.getArray(DataType.STRING), SummaryTransform.POSITIONS, "pos_zcurve");
        model.assertSummaryField("my_pos.distance", DataType.INT, SummaryTransform.DISTANCE, "pos_zcurve");
    }

    @Test
    void test_imported_pos_summary() {
        SearchModel model = new SearchModel();
        model.addSummaryField("my_pos", PositionDataType.INSTANCE, null, null);
        model.resolve();
        model.assertSummaryField("my_pos", PositionDataType.INSTANCE, SummaryTransform.GEOPOS, "my_pos_zcurve");
        model.assertSummaryField("my_pos.position", DataType.getArray(DataType.STRING), SummaryTransform.POSITIONS, "my_pos_zcurve");
        model.assertSummaryField("my_pos.distance", DataType.INT, SummaryTransform.DISTANCE, "my_pos_zcurve");
    }

    @Test
    void test_imported_pos_summary_bad_source() {
        SearchModel model = new SearchModel();
        model.addSummaryField("my_pos", PositionDataType.INSTANCE, null, "pos");
        model.resolve();
        // SummaryFieldsMustHaveValidSource processing not run in this test.
        model.assertSummaryField("my_pos", PositionDataType.INSTANCE, SummaryTransform.NONE, "pos");
        model.assertNoSummaryField("my_pos.position");
        model.assertNoSummaryField("my_pos.distance");
    }

    @Test
    void test_imported_pos_summary_bad_datatype() {
        SearchModel model = new SearchModel();
        model.addSummaryField("my_pos", DataType.getArray(PositionDataType.INSTANCE), null, "pos");
        model.resolve();
        model.assertSummaryField("my_pos", DataType.getArray(PositionDataType.INSTANCE), SummaryTransform.NONE, "pos");
        model.assertNoSummaryField("my_pos.position");
        model.assertNoSummaryField("my_pos.distance");
    }

    @Test
    void test_pos_summary_no_attr_no_rename() {
        SearchModel model = new SearchModel(false, false, false);
        model.addSummaryField("pos", PositionDataType.INSTANCE, null, "pos");
        model.resolve();
        model.assertSummaryField("pos", PositionDataType.INSTANCE, SummaryTransform.NONE, "pos");
        model.assertNoSummaryField("pos.position");
        model.assertNoSummaryField("pos.distance");
    }

    @Test
    void test_pos_default_summary_no_attr_no_rename() {
        SearchModel model = new SearchModel(false, false, false);
        model.resolve();
        assertNull(model.childSchema.getSummary("default")); // ImplicitSummaries processing not run in this test
    }

    @Test
    void test_pos_summary_no_rename() {
        SearchModel model = new SearchModel(false, true, false);
        model.addSummaryField("pos", PositionDataType.INSTANCE, null, "pos");
        model.resolve();
        model.assertSummaryField("pos", PositionDataType.INSTANCE, SummaryTransform.GEOPOS, "pos_zcurve");
        model.assertSummaryField("pos.position", DataType.getArray(DataType.STRING), SummaryTransform.POSITIONS, "pos_zcurve");
        model.assertSummaryField("pos.distance", DataType.INT, SummaryTransform.DISTANCE, "pos_zcurve");
    }

    @Test
    void test_pos_summary_no_attr() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            SearchModel model = new SearchModel(false, false, false);
            model.addSummaryField("my_pos", PositionDataType.INSTANCE, null, "pos");
            model.resolve();
        });
        assertTrue(exception.getMessage().contains("For schema 'child', field 'my_pos': No position attribute 'pos_zcurve'"));
    }

    @Test
    void test_pos_summary_bad_attr() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            SearchModel model = new SearchModel(false, false, true);
            model.addSummaryField("my_pos", PositionDataType.INSTANCE, null, "pos");
            model.resolve();
        });
        assertTrue(exception.getMessage().contains("For schema 'child', field 'my_pos': No position attribute 'pos_zcurve'"));
    }

    @Test
    void test_imported_pos_summary_no_attr() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            SearchModel model = new SearchModel(true, false, false);
            model.addSummaryField("my_pos", PositionDataType.INSTANCE, null, null);
            model.resolve();
        });
        assertTrue(exception.getMessage().contains("For schema 'child', import field 'my_pos_zcurve': "
                + "Field 'pos_zcurve' via reference field 'ref': Not found"));
    }

    @Test
    void test_imported_pos_summary_bad_attr() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            SearchModel model = new SearchModel(true, false, true);
            model.addSummaryField("my_pos", PositionDataType.INSTANCE, null, null);
            model.resolve();
        });
        assertTrue(exception.getMessage().contains("For schema 'child', field 'my_pos': "
                + "No position attribute 'my_pos_zcurve'"));
    }

    @Test
    void test_my_pos_position_summary_bad_datatype() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            SearchModel model = new SearchModel();
            model.addSummaryField("my_pos", PositionDataType.INSTANCE, null, null);
            model.addSummaryField("my_pos.position", DataType.STRING, null, "pos");
            model.resolve();
        });
        assertTrue(exception.getMessage().contains("For schema 'child', field 'my_pos.position': "
                + "exists with type 'datatype string (code: 2)', should be of type 'datatype Array<string> (code: -1486737430)"));
    }

    @Test
    void test_my_pos_position_summary_bad_transform() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            SearchModel model = new SearchModel();
            model.addSummaryField("my_pos", PositionDataType.INSTANCE, null, null);
            model.addSummaryField("my_pos.position", DataType.getArray(DataType.STRING), null, "pos");
            model.resolve();
        });
        assertTrue(exception.getMessage().contains("For schema 'child', field 'my_pos.position': "
                + "has summary transform 'none', should have transform 'positions'"));
    }

    @Test
    void test_my_pos_position_summary_bad_source() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            SearchModel model = new SearchModel();
            model.addSummaryField("my_pos", PositionDataType.INSTANCE, null, null);
            model.addSummaryField("my_pos.position", DataType.getArray(DataType.STRING), SummaryTransform.POSITIONS, "pos");
            model.resolve();
        });
        assertTrue(exception.getMessage().contains("For schema 'child', field 'my_pos.position': "
                + "has source '[source field 'pos']', should have source 'source field 'my_pos_zcurve''"));
    }

    static class SearchModel extends ParentChildSearchModel {

        SearchModel() {
            this(true);
        }

        SearchModel(boolean importedPos) {
            this(importedPos, true, false);
        }

        SearchModel(boolean importedPos, boolean setupPosAttr, boolean setupBadAttr) {
            super();
            if (importedPos) {
                createPositionField(parentSchema, setupPosAttr, setupBadAttr);
            }
            addRefField(childSchema, parentSchema, "ref");
            if (importedPos) {
                addImportedField("my_pos", "ref", "pos");
            } else {
                createPositionField(childSchema, setupPosAttr, setupBadAttr);
            }
        }

        private void createPositionField(Schema schema, boolean setupPosAttr, boolean setupBadAttr) {
            String ilScript = setupPosAttr ? "{ summary | attribute }" : "{ summary }";
            var doc = schema.getDocument();
            doc.addField(createField(doc, "pos", PositionDataType.INSTANCE, ilScript));
            if (setupBadAttr) {
                doc.addField(createField(doc, "pos_zcurve", DataType.LONG, "{ attribute }"));
            }
        }

        void addSummaryField(String fieldName, DataType dataType, SummaryTransform transform, String source) {
            addSummaryField("my_summary", fieldName, dataType, transform, source);
        }

        public void addSummaryField(String summaryName, String fieldName, DataType dataType, SummaryTransform transform, String source) {
            DocumentSummary summary = childSchema.getSummary(summaryName);
            if (summary == null) {
                summary = new DocumentSummary(summaryName, childSchema);
                childSchema.addSummary(summary);
            }
            SummaryField summaryField = new SummaryField(fieldName, dataType);
            if (source != null) {
                summaryField.addSource(source);
            }
            if (transform != null) {
                summaryField.setTransform(transform);
            }
            summary.add(summaryField);
        }

        public void assertNoSummaryField(String fieldName) {
            assertNoSummaryField("my_summary", fieldName);
        }

        public void assertNoSummaryField(String summaryName, String fieldName) {
            DocumentSummary summary = childSchema.getSummary(summaryName);
            assertNotNull(summary);
            SummaryField summaryField = summary.getSummaryField(fieldName);
            assertNull(summaryField);
        }

        public void assertSummaryField(String fieldName, DataType dataType, SummaryTransform transform, String source) {
            assertSummaryField("my_summary", fieldName, dataType, transform, source);
        }

        public void assertSummaryField(String summaryName, String fieldName, DataType dataType, SummaryTransform transform, String source) {
            DocumentSummary summary = childSchema.getSummary(summaryName);
            assertNotNull(summary);
            SummaryField summaryField = summary.getSummaryField(fieldName);
            assertNotNull(summaryField);
            assertEquals(dataType, summaryField.getDataType());
            assertEquals(transform, summaryField.getTransform());
            if (source == null) {
                assertEquals(0, summaryField.getSourceCount());
            } else {
                assertEquals(1, summaryField.getSourceCount());
                assertEquals(source, summaryField.getSingleSource());
            }
        }

        public void resolve() {
            resolve(parentSchema);
            resolve(childSchema);
        }

        private static void resolve(Schema schema) {
            new CreatePositionZCurve(schema, null, null, null).process(true, false);
            assertNotNull(schema.temporaryImportedFields().get());
            assertFalse(schema.importedFields().isPresent());
            new ImportedFieldsResolver(schema, null, null, null).process(true, false);
            assertNotNull(schema.importedFields().get());
            new AdjustPositionSummaryFields(schema, null, null, null).process(true, false);
        }
    }
}
