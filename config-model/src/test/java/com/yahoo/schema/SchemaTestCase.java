// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.schema.document.Stemming;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.schema.processing.ImportedFieldsResolver;
import com.yahoo.schema.processing.OnnxModelTypeResolver;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.model.test.utils.DeployLoggerStub;
import org.junit.jupiter.api.Test;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Schema tests that don't depend on files.
 *
 * @author bratseth
 */
public class SchemaTestCase {

    @Test
    void testValidationOfInheritedSchema() throws ParseException {
        try {
            String schema = joinLines(
                    "schema test inherits nonesuch {" +
                            "  document test inherits nonesuch {" +
                            "  }" +
                            "}");
            DeployLoggerStub logger = new DeployLoggerStub();
            ApplicationBuilder.createFromStrings(logger, schema);
            assertEquals("schema 'test' inherits 'nonesuch', but this schema does not exist",
                    logger.entries.get(0).message);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("schema 'test' inherits 'nonesuch', but this schema does not exist", e.getMessage());
        }
    }

    @Test
    void testValidationOfSchemaAndDocumentInheritanceConsistency() throws ParseException {
        try {
            String parent = joinLines(
                    "schema parent {" +
                            "  document parent {" +
                            "    field pf1 type string {" +
                            "      indexing: summary" +
                            "    }" +
                            "  }" +
                            "}");
            String child = joinLines(
                    "schema child inherits parent {" +
                            "  document child {" +
                            "    field cf1 type string {" +
                            "      indexing: summary" +
                            "    }" +
                            "  }" +
                            "}");
            ApplicationBuilder.createFromStrings(new DeployLoggerStub(), parent, child);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("schema 'child' inherits 'parent', " +
                    "but its document type does not inherit the parent's document type"
            , e.getMessage());
        }
    }

    @Test
    void testOnnxModelWithColonInput() throws Exception {
        String schema =
                """
                schema msmarco {
                    document msmarco {
                        field id type string {
                            indexing: summary | attribute
                        }
                        field text type string {
                            indexing: summary | index
                        }
                    }
                    onnx-model ltr_tensorflow {
                        file: files/ltr_tensorflow.onnx
                        input input:0: vespa_input
                        output dense: dense
                    }
                    rank-profile tensorflow {
                        function vespa_input() {
                            expression {
                                tensor<float>(x[1],y[3]):[[fieldMatch(text).queryCompleteness, fieldMatch(text).significance, nativeRank(text)]]
                            }
                        }
                        first-phase {
                            expression: sum(onnx(ltr_tensorflow).dense)
                        }
                        summary-features {
                            onnx(ltr_tensorflow)
                            fieldMatch(text).queryCompleteness
                            fieldMatch(text).significance
                            nativeRank(text)
                        }
                    }
                }""";
        ApplicationBuilder builder = new ApplicationBuilder(new DeployLoggerStub());
        builder.processorsToSkip().add(OnnxModelTypeResolver.class); // Avoid discovering the Onnx model referenced does not exist
        builder.addSchema(schema);
        var application = builder.build(true);
        var input = application.schemas().get("msmarco").onnxModels().get("ltr_tensorflow").getInputMap().get("input:0");
        assertNotNull(input);
        assertEquals("vespa_input", input);
    }

    @Test
    void testSchemaInheritance() throws ParseException {
        String parentLines = joinLines(
                "schema parent {" +
                        "  document parent {" +
                        "    field pf1 type string {" +
                        "      indexing: summary" +
                        "    }" +
                        "  }" +
                        "  fieldset parent_set {" +
                        "    fields: pf1" +
                        "  }" +
                        "  stemming: none" +
                        "  index parent_index {" +
                        "    stemming: best" +
                        "  }" +
                        "  field parent_field type string {" +
                        "      indexing: input pf1 | lowercase | index | attribute | summary" +
                        "  }" +
                        "  rank-profile parent_profile {" +
                        "  }" +
                        "  constant parent_constant {" +
                        "    file: constants/my_constant_tensor_file.json" +
                        "    type: tensor<float>(x{},y{})" +
                        "  }" +
                        "  onnx-model parent_model {" +
                        "    file: models/my_model.onnx" +
                        "  }" +
                        "  document-summary parent_summary {" +
                        "    summary pf1 type string {}" +
                        "  }" +
                        "  import field parentschema_ref.name as parent_imported {}" +
                        "  raw-as-base64-in-summary" +
                        "}");
        String child1Lines = joinLines(
                "schema child1 inherits parent {" +
                        "  document child1 inherits parent {" +
                        "    field c1f1 type string {" +
                        "      indexing: summary" +
                        "    }" +
                        "  }" +
                        "  fieldset child1_set {" +
                        "    fields: c1f1, pf1" +
                        "  }" +
                        "  stemming: shortest" +
                        "  index child1_index {" +
                        "    stemming: shortest" +
                        "  }" +
                        "  field child1_field type string {" +
                        "      indexing: input pf1 | lowercase | index | attribute | summary" +
                        "  }" +
                        "  rank-profile child1_profile inherits parent_profile {" +
                        "    constants {" +
                        "      child1_constant tensor<float>(x{},y{}): file:constants/my_constant_tensor_file.json" +
                        "    }" +
                        "  }" +
                        "  onnx-model child1_model {" +
                        "    file: models/my_model.onnx" +
                        "  }" +
                        "  document-summary child1_summary inherits parent_summary {" +
                        "    summary c1f1 type string {}" +
                        "  }" +
                        "  import field parentschema_ref.name as child1_imported {}" +
                        "}");
        String child2Lines = joinLines(
                "schema child2 inherits parent {" +
                        "  document child2 inherits parent {" +
                        "    field c2f1 type string {" +
                        "      indexing: summary" +
                        "    }" +
                        "  }" +
                        "  fieldset child2_set {" +
                        "    fields: c2f1, pf1" +
                        "  }" +
                        "  stemming: shortest" +
                        "  index child2_index {" +
                        "    stemming: shortest" +
                        "  }" +
                        "  field child2_field type string {" +
                        "      indexing: input pf1 | lowercase | index | attribute | summary" +
                        "  }" +
                        "  rank-profile child2_profile inherits parent_profile {" +
                        "  }" +
                        "  constant child2_constant {" +
                        "    file: constants/my_constant_tensor_file.json" +
                        "    type: tensor<float>(x{},y{})" +
                        "  }" +
                        "  onnx-model child2_model {" +
                        "    file: models/my_model.onnx" +
                        "  }" +
                        "  document-summary child2_summary inherits parent_summary {" +
                        "    summary c2f1 type string {}" +
                        "  }" +
                        "  import field parentschema_ref.name as child2_imported {}" +
                        "}");

        ApplicationBuilder builder = new ApplicationBuilder(new DeployLoggerStub());
        builder.processorsToSkip().add(OnnxModelTypeResolver.class); // Avoid discovering the Onnx model referenced does not exist
        builder.processorsToSkip().add(ImportedFieldsResolver.class); // Avoid discovering the document reference leads nowhere
        builder.addSchema(parentLines);
        builder.addSchema(child1Lines);
        builder.addSchema(child2Lines);
        builder.build(true);
        var application = builder.application();

        var child1 = application.schemas().get("child1");
        assertEquals("pf1", child1.fieldSets().userFieldSets().get("parent_set").getFieldNames().stream().findFirst().get());
        assertEquals("[c1f1, pf1]", child1.fieldSets().userFieldSets().get("child1_set").getFieldNames().toString());
        assertEquals(Stemming.SHORTEST, child1.getStemming());
        assertEquals(Stemming.BEST, child1.getIndex("parent_index").getStemming());
        assertEquals(Stemming.SHORTEST, child1.getIndex("child1_index").getStemming());
        assertNotNull(child1.getField("parent_field"));
        assertNotNull(child1.getField("child1_field"));
        assertNotNull(child1.getExtraField("parent_field"));
        assertNotNull(child1.getExtraField("child1_field"));
        assertNotNull(builder.getRankProfileRegistry().get(child1, "parent_profile"));
        assertNotNull(builder.getRankProfileRegistry().get(child1, "child1_profile"));
        var child1profile = builder.getRankProfileRegistry().get(child1, "child1_profile");
        assertEquals("parent_profile", builder.getRankProfileRegistry().get(child1, "child1_profile").inheritedNames().get(0));
        assertNotNull(child1.constants().get(FeatureNames.asConstantFeature("parent_constant")));
        assertNotNull(child1profile.constants().get(FeatureNames.asConstantFeature("child1_constant")));
        assertTrue(child1.constants().containsKey(FeatureNames.asConstantFeature("parent_constant")));
        assertTrue(child1profile.constants().containsKey(FeatureNames.asConstantFeature("child1_constant")));
        assertTrue(child1profile.constants().containsKey(FeatureNames.asConstantFeature("parent_constant")));
        assertNotNull(child1.onnxModels().get("parent_model"));
        assertNotNull(child1.onnxModels().get("child1_model"));
        assertTrue(child1.onnxModels().containsKey("parent_model"));
        assertTrue(child1.onnxModels().containsKey("child1_model"));
        assertNotNull(child1.getSummary("parent_summary"));
        assertNotNull(child1.getSummary("child1_summary"));
        assertEquals("parent_summary", child1.getSummary("child1_summary").inherited().get().getName());
        assertTrue(child1.getSummaries().containsKey("parent_summary"));
        assertTrue(child1.getSummaries().containsKey("child1_summary"));
        assertNotNull(child1.getSummaryField("pf1"));
        assertNotNull(child1.getSummaryField("c1f1"));
        assertNotNull(child1.getExplicitSummaryField("pf1"));
        assertNotNull(child1.getExplicitSummaryField("c1f1"));
        assertNotNull(child1.getUniqueNamedSummaryFields().get("pf1"));
        assertNotNull(child1.getUniqueNamedSummaryFields().get("c1f1"));
        assertNotNull(child1.temporaryImportedFields().get().fields().get("parent_imported"));
        assertNotNull(child1.temporaryImportedFields().get().fields().get("child1_imported"));

        var child2 = application.schemas().get("child2");
        assertEquals("pf1", child2.fieldSets().userFieldSets().get("parent_set").getFieldNames().stream().findFirst().get());
        assertEquals("[c2f1, pf1]", child2.fieldSets().userFieldSets().get("child2_set").getFieldNames().toString());
        assertEquals(Stemming.SHORTEST, child2.getStemming());
        assertEquals(Stemming.BEST, child2.getIndex("parent_index").getStemming());
        assertEquals(Stemming.SHORTEST, child2.getIndex("child2_index").getStemming());
        assertNotNull(child2.getField("parent_field"));
        assertNotNull(child2.getField("child2_field"));
        assertNotNull(child2.getExtraField("parent_field"));
        assertNotNull(child2.getExtraField("child2_field"));
        assertNotNull(builder.getRankProfileRegistry().get(child2, "parent_profile"));
        assertNotNull(builder.getRankProfileRegistry().get(child2, "child2_profile"));
        assertEquals("parent_profile", builder.getRankProfileRegistry().get(child2, "child2_profile").inheritedNames().get(0));
        assertNotNull(child2.constants().get(FeatureNames.asConstantFeature("parent_constant")));
        assertNotNull(child2.constants().get(FeatureNames.asConstantFeature("child2_constant")));
        assertTrue(child2.constants().containsKey(FeatureNames.asConstantFeature("parent_constant")));
        assertTrue(child2.constants().containsKey(FeatureNames.asConstantFeature("child2_constant")));
        assertNotNull(child2.onnxModels().get("parent_model"));
        assertNotNull(child2.onnxModels().get("child2_model"));
        assertTrue(child2.onnxModels().containsKey("parent_model"));
        assertTrue(child2.onnxModels().containsKey("child2_model"));
        assertNotNull(child2.getSummary("parent_summary"));
        assertNotNull(child2.getSummary("child2_summary"));
        assertEquals("parent_summary", child2.getSummary("child2_summary").inherited().get().getName());
        assertTrue(child2.getSummaries().containsKey("parent_summary"));
        assertTrue(child2.getSummaries().containsKey("child2_summary"));
        assertNotNull(child2.getSummaryField("pf1"));
        assertNotNull(child2.getSummaryField("c2f1"));
        assertNotNull(child2.getExplicitSummaryField("pf1"));
        assertNotNull(child2.getExplicitSummaryField("c2f1"));
        assertNotNull(child2.getUniqueNamedSummaryFields().get("pf1"));
        assertNotNull(child2.getUniqueNamedSummaryFields().get("c2f1"));
        assertNotNull(child2.temporaryImportedFields().get().fields().get("parent_imported"));
        assertNotNull(child2.temporaryImportedFields().get().fields().get("child2_imported"));
        DocumentSummary child2DefaultSummary = child2.getSummary("default");
        assertEquals(6, child2DefaultSummary.getSummaryFields().size());
        assertTrue(child2DefaultSummary.getSummaryFields().containsKey("child2_field"));
        assertTrue(child2DefaultSummary.getSummaryFields().containsKey("parent_field"));
        assertTrue(child2DefaultSummary.getSummaryFields().containsKey("pf1"));
        assertTrue(child2DefaultSummary.getSummaryFields().containsKey("c2f1"));
        DocumentSummary child2AttributeprefetchSummary = child2.getSummary("attributeprefetch");
        assertEquals(4, child2AttributeprefetchSummary.getSummaryFields().size());
        assertTrue(child2AttributeprefetchSummary.getSummaryFields().containsKey("child2_field"));
        assertTrue(child2AttributeprefetchSummary.getSummaryFields().containsKey("parent_field"));
    }

    @Test
    void testSchemaInheritanceEmptyChildren() throws ParseException {
        String parentLines = joinLines(
                "schema parent {" +
                        "  document parent {" +
                        "    field pf1 type string {" +
                        "      indexing: summary" +
                        "    }" +
                        "  }" +
                        "  fieldset parent_set {" +
                        "    fields: pf1" +
                        "  }" +
                        "  stemming: none" +
                        "  index parent_index {" +
                        "    stemming: best" +
                        "  }" +
                        "  field parent_field type string {" +
                        "      indexing: input pf1 | lowercase | index | attribute | summary" +
                        "  }" +
                        "  rank-profile parent_profile {" +
                        "  }" +
                        "  constant parent_constant {" +
                        "    file: constants/my_constant_tensor_file.json" +
                        "    type: tensor<float>(x{},y{})" +
                        "  }" +
                        "  onnx-model parent_model {" +
                        "    file: models/my_model.onnx" +
                        "  }" +
                        "  document-summary parent_summary {" +
                        "    summary pf1 type string {}" +
                        "  }" +
                        "  import field parentschema_ref.name as parent_imported {}" +
                        "  raw-as-base64-in-summary" +
                        "}");
        String childLines = joinLines(
                "schema child inherits parent {" +
                        "  document child inherits parent {" +
                        "    field cf1 type string {" +
                        "      indexing: summary" +
                        "    }" +
                        "  }" +
                        "}");
        String grandchildLines = joinLines(
                "schema grandchild inherits child {" +
                        "  document grandchild inherits child {" +
                        "    field gf1 type string {" +
                        "      indexing: summary" +
                        "    }" +
                        "  }" +
                        "}");

        ApplicationBuilder builder = new ApplicationBuilder(new DeployLoggerStub());
        builder.processorsToSkip().add(OnnxModelTypeResolver.class); // Avoid discovering the Onnx model referenced does not exist
        builder.processorsToSkip().add(ImportedFieldsResolver.class); // Avoid discovering the document reference leads nowhere
        builder.addSchema(parentLines);
        builder.addSchema(childLines);
        builder.addSchema(grandchildLines);
        builder.build(true);
        var application = builder.application();

        assertInheritedFromParent(application.schemas().get("child"), builder.getRankProfileRegistry());
        assertInheritedFromParent(application.schemas().get("grandchild"), builder.getRankProfileRegistry());
    }

    @Test
    void testInheritingMultipleRankProfilesWithOverlappingConstructsIsDisallowed1() throws ParseException {
        try {
            String profile = joinLines(
                    "schema test {" +
                            "  document test {" +
                            "    field title type string {" +
                            "      indexing: summary" +
                            "    }" +
                            "  }" +
                            "  rank-profile r1 {" +
                            "    first-phase {" +
                            "      expression: fieldMatch(title)" +
                            "    }" +
                            "  }" +
                            "  rank-profile r2 {" +
                            "    first-phase {" +
                            "      expression: fieldMatch(title)" +
                            "    }" +
                            "  }" +
                            "  rank-profile r3 inherits r1, r2 {" +
                            "  }" +
                            "}");
            ApplicationBuilder.createFromStrings(new DeployLoggerStub(), profile);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Only one of the profiles inherited by rank profile 'r3' can contain first-phase expression, but it is present in multiple",
                    e.getMessage());
        }
    }

    @Test
    void testInheritingMultipleRankProfilesWithOverlappingConstructsIsAllowedWhenDefinedInChild() throws ParseException {
        String profile = joinLines(
                "schema test {" +
                        "  document test {" +
                        "    field title type string {" +
                        "      indexing: summary" +
                        "    }" +
                        "    field myFilter type string {" +
                        "      indexing: attribute\n" +
                        "      rank: filter" +
                        "    }" +
                        "  }" +
                        "  rank-profile r1 {" +
                        "    first-phase {" +
                        "      expression: fieldMatch(title)" +
                        "    }" +
                        "  }" +
                        "  rank-profile r2 {" +
                        "    first-phase {" +
                        "      expression: fieldMatch(title)" +
                        "    }" +
                        "  }" +
                        "  rank-profile r3 inherits r1, r2 {" +
                        "    first-phase {" + // Redefined here so this does not cause failure
                        "      expression: nativeRank" +
                        "    }" +
                        "  }" +
                        "}");
        var builder = ApplicationBuilder.createFromStrings(new DeployLoggerStub(), profile);
        var r3 = builder.getRankProfileRegistry().resolve(builder.application().schemas().get("test").getDocument(), "r3");
        assertEquals(1, r3.allFilterFields().size());
    }

    @Test
    void testInheritingMultipleRankProfilesWithOverlappingConstructsIsDisallowed2() throws ParseException {
        try {
            String profile = joinLines(
                    "schema test {" +
                            "  document test {" +
                            "    field title type string {" +
                            "      indexing: summary" +
                            "    }" +
                            "  }" +
                            "  rank-profile r1 {" +
                            "    function f1() {" +
                            "      expression: fieldMatch(title)" +
                            "    }" +
                            "  }" +
                            "  rank-profile r2 {" +
                            "    function f1() {" +
                            "      expression: fieldMatch(title)" +
                            "    }" +
                            "  }" +
                            "  rank-profile r3 inherits r1, r2 {" +
                            "  }" +
                            "}");
            ApplicationBuilder.createFromStrings(new DeployLoggerStub(), profile);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("rank profile 'r3' inherits rank profile 'r2' which contains function 'f1', but this function is already defined in another profile this inherits",
                    e.getMessage());
        }
    }

    private void assertInheritedFromParent(Schema schema, RankProfileRegistry rankProfileRegistry) {
        assertEquals("pf1", schema.fieldSets().userFieldSets().get("parent_set").getFieldNames().stream().findFirst().get());
        assertEquals(Stemming.NONE, schema.getStemming());
        assertEquals(Stemming.BEST, schema.getIndex("parent_index").getStemming());
        assertNotNull(schema.getField("parent_field"));
        assertNotNull(schema.getExtraField("parent_field"));
        assertNotNull(rankProfileRegistry.get(schema, "parent_profile"));
        assertNotNull(schema.constants().get(FeatureNames.asConstantFeature("parent_constant")));
        assertTrue(schema.constants().containsKey(FeatureNames.asConstantFeature("parent_constant")));
        assertNotNull(schema.onnxModels().get("parent_model"));
        assertTrue(schema.onnxModels().containsKey("parent_model"));
        assertNotNull(schema.getSummary("parent_summary"));
        assertTrue(schema.getSummaries().containsKey("parent_summary"));
        assertNotNull(schema.getSummaryField("pf1"));
        assertNotNull(schema.getExplicitSummaryField("pf1"));
        assertNotNull(schema.getUniqueNamedSummaryFields().get("pf1"));
        assertNotNull(schema.temporaryImportedFields().get().fields().get("parent_imported"));
        assertTrue(schema.isRawAsBase64());
    }

}
