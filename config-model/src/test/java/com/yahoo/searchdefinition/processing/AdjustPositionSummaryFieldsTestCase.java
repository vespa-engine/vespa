// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.document.DataType;
import com.yahoo.document.PositionDataType;
import com.yahoo.searchdefinition.Search;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class AdjustPositionSummaryFieldsTestCase {

    @Test
    public void test_pos_summary() {
        SearchModel model = new SearchModel(false);
        model.addSummaryField("my_pos", PositionDataType.INSTANCE, null, "pos");
        model.resolve();
        model.assertSummaryField("my_pos", PositionDataType.INSTANCE, SummaryTransform.GEOPOS, "pos_zcurve");
        model.assertSummaryField("my_pos.position", DataType.getArray(DataType.STRING), SummaryTransform.POSITIONS, "pos_zcurve");
        model.assertSummaryField("my_pos.distance", DataType.INT, SummaryTransform.DISTANCE, "pos_zcurve");
    }

    @Test
    public void test_imported_pos_summary() {
        SearchModel model = new SearchModel();
        model.addSummaryField("my_pos", PositionDataType.INSTANCE, null, null);
        model.resolve();
        model.assertSummaryField("my_pos", PositionDataType.INSTANCE, SummaryTransform.GEOPOS, "my_pos_zcurve");
        model.assertSummaryField("my_pos.position", DataType.getArray(DataType.STRING), SummaryTransform.POSITIONS, "my_pos_zcurve");
        model.assertSummaryField("my_pos.distance", DataType.INT, SummaryTransform.DISTANCE, "my_pos_zcurve");
    }

    @Test
    public void test_imported_pos_summary_bad_source() {
        SearchModel model = new SearchModel();
        model.addSummaryField("my_pos", PositionDataType.INSTANCE, null, "pos");
        model.resolve();
        // SummaryFieldsMustHaveValidSource processing not run in this test.
        model.assertSummaryField("my_pos", PositionDataType.INSTANCE, SummaryTransform.NONE, "pos");
        model.assertNoSummaryField("my_pos.position");
        model.assertNoSummaryField("my_pos.distance");
    }

    @Test
    public void test_imported_pos_summary_bad_datatype() {
        SearchModel model = new SearchModel();
        model.addSummaryField("my_pos", DataType.getArray(PositionDataType.INSTANCE), null, "pos");
        model.resolve();
        model.assertSummaryField("my_pos", DataType.getArray(PositionDataType.INSTANCE), SummaryTransform.NONE, "pos");
        model.assertNoSummaryField("my_pos.position");
        model.assertNoSummaryField("my_pos.distance");
    }

    @Test
    public void test_pos_summary_no_attr_no_rename() {
        SearchModel model = new SearchModel(false, false, false);
        model.addSummaryField("pos", PositionDataType.INSTANCE, null, "pos");
        model.resolve();
        model.assertSummaryField("pos", PositionDataType.INSTANCE, SummaryTransform.NONE, "pos");
        model.assertNoSummaryField("pos.position");
        model.assertNoSummaryField("pos.distance");
    }

    @Test
    public void test_pos_default_summary_no_attr_no_rename() {
        SearchModel model = new SearchModel(false, false, false);
        model.resolve();
        assertNull(model.childSearch.getSummary("default")); // ImplicitSummaries processing not run in this test
    }

    @Test
    public void test_pos_summary_no_rename() {
        SearchModel model = new SearchModel(false, true, false);
        model.addSummaryField("pos", PositionDataType.INSTANCE, null, "pos");
        model.resolve();
        model.assertSummaryField("pos", PositionDataType.INSTANCE, SummaryTransform.GEOPOS, "pos_zcurve");
        model.assertSummaryField("pos.position", DataType.getArray(DataType.STRING), SummaryTransform.POSITIONS, "pos_zcurve");
        model.assertSummaryField("pos.distance", DataType.INT, SummaryTransform.DISTANCE, "pos_zcurve");
    }

    @Rule
    public final ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void test_pos_summary_no_attr() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("For search 'child', field 'my_pos': "
                + "No position attribute 'pos_zcurve'");
        SearchModel model = new SearchModel(false, false, false);
        model.addSummaryField("my_pos", PositionDataType.INSTANCE, null, "pos");
        model.resolve();
    }

    @Test
    public void test_pos_summary_bad_attr() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("For search 'child', field 'my_pos': "
                + "No position attribute 'pos_zcurve'");
        SearchModel model = new SearchModel(false, false, true);
        model.addSummaryField("my_pos", PositionDataType.INSTANCE, null, "pos");
        model.resolve();
    }

    @Test
    public void test_imported_pos_summary_no_attr() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("For search 'child', import field 'my_pos_zcurve': "
                + "Field 'pos_zcurve' via reference field 'ref': Not found");
        SearchModel model = new SearchModel(true, false, false);
        model.addSummaryField("my_pos", PositionDataType.INSTANCE, null, null);
        model.resolve();
    }

    @Test
    public void test_imported_pos_summary_bad_attr() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("For search 'child', field 'my_pos': "
                + "No position attribute 'my_pos_zcurve'");
        SearchModel model = new SearchModel(true, false, true);
        model.addSummaryField("my_pos", PositionDataType.INSTANCE, null, null);
        model.resolve();
    }

    @Test
    public void test_my_pos_position_summary_bad_datatype() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("For search 'child', field 'my_pos.position': "
                + "exists with type 'datatype string (code: 2)', should be of type 'datatype Array<string> (code: -1486737430)");
        SearchModel model = new SearchModel();
        model.addSummaryField("my_pos", PositionDataType.INSTANCE, null, null);
        model.addSummaryField("my_pos.position", DataType.STRING, null, "pos");
        model.resolve();
    }

    @Test
    public void test_my_pos_position_summary_bad_transform() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("For search 'child', field 'my_pos.position': "
                + "has summary transform 'none', should have transform 'positions'");
        SearchModel model = new SearchModel();
        model.addSummaryField("my_pos", PositionDataType.INSTANCE, null, null);
        model.addSummaryField("my_pos.position", DataType.getArray(DataType.STRING), null, "pos");
        model.resolve();
    }

    @Test
    public void test_my_pos_position_summary_bad_source() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("For search 'child', field 'my_pos.position': "
                + "has source '[source field 'pos']', should have source 'source field 'my_pos_zcurve''");
        SearchModel model = new SearchModel();
        model.addSummaryField("my_pos", PositionDataType.INSTANCE, null, null);
        model.addSummaryField("my_pos.position", DataType.getArray(DataType.STRING), SummaryTransform.POSITIONS, "pos");
        model.resolve();
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
                createPositionField(parentSearch, setupPosAttr, setupBadAttr);
            }
            addRefField(childSearch, parentSearch, "ref");
            if (importedPos) {
                addImportedField("my_pos", "ref", "pos");
            } else {
                createPositionField(childSearch, setupPosAttr, setupBadAttr);
            }
        }

        private void createPositionField(Search search, boolean setupPosAttr, boolean setupBadAttr) {
            String ilScript = setupPosAttr ? "{ summary | attribute }" : "{ summary }";
            search.getDocument().addField(createField("pos", PositionDataType.INSTANCE, ilScript));
            if (setupBadAttr) {
                search.getDocument().addField(createField("pos_zcurve", DataType.LONG, "{ attribute }"));
            }
        }

        void addSummaryField(String fieldName, DataType dataType, SummaryTransform transform, String source) {
            addSummaryField("my_summary", fieldName, dataType, transform, source);
        }

        public void addSummaryField(String summaryName, String fieldName, DataType dataType, SummaryTransform transform, String source) {
            DocumentSummary summary = childSearch.getSummary(summaryName);
            if (summary == null) {
                summary = new DocumentSummary(summaryName);
                childSearch.addSummary(summary);
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
            DocumentSummary summary = childSearch.getSummary(summaryName);
            assertNotNull(summary);
            SummaryField summaryField = summary.getSummaryField(fieldName);
            assertNull(summaryField);
        }

        public void assertSummaryField(String fieldName, DataType dataType, SummaryTransform transform, String source) {
            assertSummaryField("my_summary", fieldName, dataType, transform, source);
        }

        public void assertSummaryField(String summaryName, String fieldName, DataType dataType, SummaryTransform transform, String source) {
            DocumentSummary summary = childSearch.getSummary(summaryName);
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
            resolve(parentSearch);
            resolve(childSearch);
        }

        private static void resolve(Search search) {
            new CreatePositionZCurve(search, null, null, null).process(true, false);
            assertNotNull(search.temporaryImportedFields().get());
            assertFalse(search.importedFields().isPresent());
            new ImportedFieldsResolver(search, null, null, null).process(true, false);
            assertFalse(search.temporaryImportedFields().isPresent());
            assertNotNull(search.importedFields().get());
            new AdjustPositionSummaryFields(search, null, null, null).process(true, false);
        }
    }
}
