// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.search.config.SchemaInfoConfig;
import com.yahoo.searchlib.ranking.features.FeatureNames;
import com.yahoo.schema.derived.DerivedConfiguration;
import com.yahoo.schema.document.ImmutableSDField;
import com.yahoo.schema.document.Stemming;
import com.yahoo.schema.document.TokensMode;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.schema.processing.ImportedFieldsResolver;
import com.yahoo.schema.processing.OnnxModelTypeResolver;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;
import com.yahoo.vespa.configmodel.producers.DocumentManager;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.model.test.utils.DeployLoggerStub;
import com.yahoo.yolean.Exceptions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
    void testOnnxModelOptimizeModel() throws Exception {
        String schema =
                """
                schema test {
                    document test {
                        field id type string {
                            indexing: summary | attribute
                        }
                    }
                    onnx-model default_model {
                        file: files/default.onnx
                    }
                    onnx-model optimized_model {
                        file: files/optimized.onnx
                        optimize-model: true
                    }
                    onnx-model unoptimized_model {
                        file: files/unoptimized.onnx
                        optimize-model: false
                    }
                }""";
        ApplicationBuilder builder = new ApplicationBuilder(new DeployLoggerStub());
        builder.processorsToSkip().add(OnnxModelTypeResolver.class); // Avoid discovering the Onnx model referenced does not exist
        builder.addSchema(schema);
        var application = builder.build(true);
        var models = application.schemas().get("test").onnxModels();
        assertTrue(models.get("default_model").getOptimizeModel());
        assertTrue(models.get("optimized_model").getOptimizeModel());
        assertFalse(models.get("unoptimized_model").getOptimizeModel());
    }

    @Test
    void testSchemaInheritance() throws ParseException {
        String parentLines = joinLines(
                "schema parent {" +
                        "  document parent {" +
                        "    field pf1 type string {" +
                        "      indexing: summary" +
                        "    }" +
                        "    field pf2 type string {" +
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
                        "  document-summary parent_summary1 {" +
                        "    summary pf1 {}" +
                        "  }" +
                        "  document-summary parent_summary2 {" +
                        "    summary pf2 {}" +
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
                        "  document-summary child1_summary inherits parent_summary1 {" +
                        "    summary c1f1 {}" +
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
                        "  document-summary child2_summary inherits parent_summary1, parent_summary2 {" +
                        "    summary c2f1 {}" +
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
        assertNotNull(child1.getSummary("parent_summary1"));
        assertNotNull(child1.getSummary("child1_summary"));
        assertEquals("parent_summary1", child1.getSummary("child1_summary").inherited().get(0).name());
        assertTrue(child1.getSummaries().containsKey("parent_summary1"));
        assertTrue(child1.getSummaries().containsKey("child1_summary"));
        assertNotNull(child1.getSummaryField("pf1"));
        assertNotNull(child1.getSummaryField("c1f1"));
        assertNotNull(child1.getExplicitSummaryField("pf1"));
        assertNotNull(child1.getExplicitSummaryField("pf2"));
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
        assertNotNull(child2.getSummary("parent_summary1"));
        assertNotNull(child2.getSummary("parent_summary2"));
        assertNotNull(child2.getSummary("child2_summary"));
        assertEquals("parent_summary1", child2.getSummary("child2_summary").inherited().get(0).name());
        assertEquals("parent_summary2", child2.getSummary("child2_summary").inherited().get(1).name());
        assertTrue(child2.getSummaries().containsKey("parent_summary1"));
        assertTrue(child2.getSummaries().containsKey("parent_summary2"));
        assertTrue(child2.getSummaries().containsKey("child2_summary"));
        assertNotNull(child2.getSummaryField("pf1"));
        assertNotNull(child2.getSummaryField("c2f1"));
        assertNotNull(child2.getExplicitSummaryField("pf1"));
        assertNotNull(child2.getExplicitSummaryField("c2f1"));
        assertNotNull(child2.getUniqueNamedSummaryFields().get("pf1"));
        assertNotNull(child2.getUniqueNamedSummaryFields().get("pf2"));
        assertNotNull(child2.getUniqueNamedSummaryFields().get("c2f1"));
        assertNotNull(child2.temporaryImportedFields().get().fields().get("parent_imported"));
        assertNotNull(child2.temporaryImportedFields().get().fields().get("child2_imported"));
        DocumentSummary child2DefaultSummary = child2.getSummary("default");
        assertEquals(7, child2DefaultSummary.getSummaryFields().size());
        assertTrue(child2DefaultSummary.getSummaryFields().containsKey("child2_field"));
        assertTrue(child2DefaultSummary.getSummaryFields().containsKey("parent_field"));
        assertTrue(child2DefaultSummary.getSummaryFields().containsKey("pf1"));
        assertTrue(child2DefaultSummary.getSummaryFields().containsKey("pf2"));
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
                        "    summary pf1 {}" +
                        "  }" +
                        "  import field parentschema_ref.name as parent_imported {}" +
                        "  documentid: attribute" +
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

    @Test
    void testInheritingRankProfileElements() throws ParseException {
        String profile = """
                    schema test {
                      document test {
                        field array1 type array<string> {
                          indexing: index
                        }
                        field array2 type array<string> {
                          indexing: index
                        }
                      }
                      rank-profile r1 {
                        rank myArray1 {
                          element-gap: 7
                        }
                        rank myArray2 {
                          element-gap: 5
                        }
                      }
                      rank-profile r2 inherits r1 {
                      }
                      rank-profile r3 inherits r1 {
                        rank myArray1 {
                          element-gap: 3
                        }
                      }
                    }
                    """;
        var application = ApplicationBuilder.createFromStrings(new DeployLoggerStub(), profile);
        var r1Gaps = application.getRankProfileRegistry().get("test", "r1").explicitFieldRankElementGaps();
        assertEquals(7, r1Gaps.get("myArray1").get().getAsInt());
        assertEquals(5, r1Gaps.get("myArray2").get().getAsInt());
        var r2Gaps = application.getRankProfileRegistry().get("test", "r2").explicitFieldRankElementGaps();
        assertEquals(7, r2Gaps.get("myArray1").get().getAsInt());
        assertEquals(5, r1Gaps.get("myArray2").get().getAsInt());
        var r3Gaps = application.getRankProfileRegistry().get("test", "r3").explicitFieldRankElementGaps();
        assertEquals(3, r3Gaps.get("myArray1").get().getAsInt());
        assertEquals(5, r1Gaps.get("myArray2").get().getAsInt());
    }

    @Test
    void testDerivingHash() throws Exception {
        String schema =
                """
                schema page {

                    field domain_hash type long {
                        indexing: input domain | hash | attribute
                    }

                    field domain_bucket type int {
                        indexing: input domain | hash | _ % 1000 | abs | attribute
                    }

                    document page {

                        field domain type string {
                            indexing: index | summary
                            match: word
                            rank: filter
                        }
                    }
                }""";
        ApplicationBuilder builder = new ApplicationBuilder(new DeployLoggerStub());
        builder.addSchema(schema);
        var application = builder.build(false); // validate=false to test config deriving without validation
        var derived = new DerivedConfiguration(application.schemas().get("page"), application.rankProfileRegistry());
        var ilConfig = new IlscriptsConfig.Builder();
        derived.getIndexingScript().getConfig(ilConfig);

        var documentModel = new DocumentModelBuilder();
        var documentManager = documentModel.build(List.of(application.schemas().get("page")));
        var documentConfig = new DocumentManager().produce(documentManager, new DocumentmanagerConfig.Builder());
    }

    @Test
    void testHashWithArithmeticAndAbs() throws Exception {
        String schema =
                """
                schema page {

                    field domain_bucket type int {
                        indexing: input domain | hash | _ % 1000 | attribute
                    }

                    field domain_abs_bucket type int {
                        indexing: input domain | hash | _ % 1000 | abs | attribute
                    }

                    field domain_long_bucket type long {
                        indexing: input domain | hash | _ % 1000 | attribute
                    }

                    field domain_long_abs_bucket type long {
                        indexing: input domain | hash | abs | _ % 1000 | attribute
                    }

                    document page {

                        field domain type string {
                            indexing: summary
                        }
                    }
                }""";
        ApplicationBuilder.createFromStrings(new DeployLoggerStub(), schema);
    }

    @Test
    void testDerivingPosition() throws Exception {
        String schema =
                """
                schema place {

                    document place {

                        field location type position {
                            indexing: attribute
                        }
                    }
                }""";
        ApplicationBuilder builder = new ApplicationBuilder(new DeployLoggerStub());
        builder.addSchema(schema);
        var application = builder.build(false); // validate=false to test config deriving without validation
        var derived = new DerivedConfiguration(application.schemas().get("place"), application.rankProfileRegistry());
        var ilConfig = new IlscriptsConfig.Builder();
        derived.getIndexingScript().getConfig(ilConfig);

        var documentModel = new DocumentModelBuilder();
        var documentManager = documentModel.build(List.of(application.schemas().get("place")));
        var documentConfig = new DocumentManager().produce(documentManager, new DocumentmanagerConfig.Builder());
    }

    @Test
    void testDerivingMultiStatementIndexing() throws Exception {
        String schema =
                """
                schema doc {

                    document doc {

                        field myString type string {
                            indexing: {
                                "en" | set_language;
                                index | summary;
                            }
                        }
                    }
                }""";
        ApplicationBuilder builder = new ApplicationBuilder(new DeployLoggerStub());
        builder.addSchema(schema);
        var application = builder.build(true);
        var derived = new DerivedConfiguration(application.schemas().get("doc"), application.rankProfileRegistry());
        var ilConfigBuilder = new IlscriptsConfig.Builder();
        derived.getIndexingScript().getConfig(ilConfigBuilder);
        assertEquals("clear_state | guard { input myString | { \"en\" | set_language; tokenize normalize stem:\"BEST\" | index myString | summary myString; }; }",
                     ilConfigBuilder.build().ilscript().get(0).content(0));
    }

    @Test
    void testCasedMatching() throws Exception {
        String schema =
                """
                schema doc {

                    document doc {

                        field myString type string {
                            indexing: index
                            match: cased
                        }
                    }
                }""";
        ApplicationBuilder builder = new ApplicationBuilder(new DeployLoggerStub());
        builder.addSchema(schema);
        var application = builder.build(true);
        var derived = new DerivedConfiguration(application.schemas().get("doc"), application.rankProfileRegistry());

        var ilConfigBuilder = new IlscriptsConfig.Builder();
        derived.getIndexingScript().getConfig(ilConfigBuilder);
        assertEquals("clear_state | guard { input myString | tokenize normalize keep-case stem:\"BEST\" | index myString; }",
                     ilConfigBuilder.build().ilscript().get(0).content(0));


        var indexInfoConfigBuilder = new IndexInfoConfig.Builder();
        derived.getIndexInfo().getConfig(indexInfoConfigBuilder);
        assertFalse(indexInfoConfigBuilder.build().toString().contains("lowercase"));
    }

    @Test
    void testLinguisticsProfile() throws Exception {
        String schema =
                """
                schema doc {

                    document doc {

                        field s1 type string {
                            indexing: index
                            linguistics {
                                profile: p1
                            }
                        }
                        field s2 type string {
                            indexing: index
                            linguistics {
                                profile: p2
                            }
                        }
                        field s3 type string {
                            indexing: index
                            linguistics {
                                profile {
                                    search: p3
                                    index: p4
                                }
                            }
                        }
                        field s4 type string {
                            indexing: index
                            linguistics {
                                profile: p3
                            }
                        }
                    }
                    fieldset fs1 {
                        fields: s3, s4
                    }
                }""";
        ApplicationBuilder builder = new ApplicationBuilder(new DeployLoggerStub());
        builder.addSchema(schema);
        var application = builder.build(true);
        var derived = new DerivedConfiguration(application.schemas().get("doc"), application.rankProfileRegistry());

        var ilConfigBuilder = new IlscriptsConfig.Builder();
        derived.getIndexingScript().getConfig(ilConfigBuilder);
        assertEquals("clear_state | guard { input s1 | tokenize normalize profile:\"p1\" stem:\"BEST\" | index s1; }",
                     ilConfigBuilder.build().ilscript().get(0).content(0));
        assertEquals("clear_state | guard { input s2 | tokenize normalize profile:\"p2\" stem:\"BEST\" | index s2; }",
                     ilConfigBuilder.build().ilscript().get(0).content(1));
        assertEquals("clear_state | guard { input s3 | tokenize normalize profile:\"p4\" stem:\"BEST\" | index s3; }",
                     ilConfigBuilder.build().ilscript().get(0).content(2));


        var indexInfoConfigBuilder = new IndexInfoConfig.Builder();
        derived.getIndexInfo().getConfig(indexInfoConfigBuilder);
        var config = indexInfoConfigBuilder.build();
        assertCommand("s1", "linguistics-profile p1", config);
        assertCommand("s2", "linguistics-profile p2", config);
        assertCommand("s3", "linguistics-profile p3", config);
        assertNoCommand("s3", "linguistics-profile p4", config);
        assertCommand("fs1", "linguistics-profile p3", config);
    }

    @Test
    void testLinguisticsTokens() throws Exception {
        String schema =
                """
                schema doc {

                    document doc {

                        field s1 type string {
                            indexing: index
                            linguistics {
                                profile: p1
                                tokens: alternatives
                            }
                        }
                        field s2 type string {
                            indexing: index
                            linguistics {
                                index {
                                    profile: p2
                                    tokens: alternatives
                                }
                                search {
                                    profile: p3
                                    tokens: original-and-alternatives
                                }
                            }
                        }
                        field s3 type string {
                            indexing: index
                            linguistics {
                                tokens: original
                            }
                        }
                        field s4 type string {
                            indexing: index
                            linguistics {
                                profile: p4
                                tokens: first-alternative
                                search {
                                    tokens: original
                                }
                            }
                        }
                    }
                }""";
        ApplicationBuilder builder = new ApplicationBuilder(new DeployLoggerStub());
        builder.addSchema(schema);
        var application = builder.build(true);
        var derived = new DerivedConfiguration(application.schemas().get("doc"), application.rankProfileRegistry());

        var ilConfigBuilder = new IlscriptsConfig.Builder();
        derived.getIndexingScript().getConfig(ilConfigBuilder);
        var ilscript = ilConfigBuilder.build().ilscript().get(0);
        assertEquals("clear_state | guard { input s1 | tokenize normalize profile:\"p1\" stem:\"ALL_STEMS\" | index s1; }",
                     ilscript.content(0));
        assertEquals("clear_state | guard { input s2 | tokenize normalize profile:\"p2\" stem:\"ALL_STEMS\" | index s2; }",
                     ilscript.content(1));
        // tokens: original means no stemming, which is not written to the script
        assertEquals("clear_state | guard { input s3 | tokenize normalize | index s3; }",
                     ilscript.content(2));
        // the common setting applies to indexing, the search block only overrides searching
        assertEquals("clear_state | guard { input s4 | tokenize normalize profile:\"p4\" stem:\"BEST\" | index s4; }",
                     ilscript.content(3));

        var indexInfoConfigBuilder = new IndexInfoConfig.Builder();
        derived.getIndexInfo().getConfig(indexInfoConfigBuilder);
        var config = indexInfoConfigBuilder.build();
        assertCommand("s1", "stem:ALL_STEMS", config);
        assertCommand("s2", "stem:ALL", config);
        assertCommand("s2", "linguistics-profile p3", config);
        assertNoCommand("s3", "stem:NONE", config);
        assertNoCommand("s3", "stem:BEST", config);
        assertNoCommand("s4", "stem:BEST", config);
    }

    @Test
    void testLinguisticsTokensOverridesStemming() throws Exception {
        String schema =
                """
                schema doc {

                    document doc {

                        field s1 type string {
                            indexing: index
                            stemming: shortest
                            linguistics {
                                tokens: alternatives
                            }
                        }
                    }
                }""";
        var logger = new DeployLoggerStub();
        ApplicationBuilder builder = new ApplicationBuilder(logger);
        builder.addSchema(schema);
        var application = builder.build(true);
        var derived = new DerivedConfiguration(application.schemas().get("doc"), application.rankProfileRegistry());

        var ilConfigBuilder = new IlscriptsConfig.Builder();
        derived.getIndexingScript().getConfig(ilConfigBuilder);
        assertEquals("clear_state | guard { input s1 | tokenize normalize stem:\"ALL_STEMS\" | index s1; }",
                     ilConfigBuilder.build().ilscript().get(0).content(0));
        assertTrue(logger.entries.stream().anyMatch(entry -> entry.message.contains("stemming: shortest is ignored")),
                   "Expected a warning about the ignored stemming setting, got " + logger.entries);
    }

    @Test
    void testLinguisticsTokensSetForOneSideOnly() throws Exception {
        String schema =
                """
                schema doc {

                    document doc {

                        field s1 type string {
                            indexing: index
                            linguistics {
                                index {
                                    tokens: alternatives
                                }
                            }
                        }
                        field s2 type string {
                            indexing: index
                            linguistics {
                                search {
                                    tokens: alternatives
                                }
                            }
                        }
                        field s3 type string {
                            indexing: index
                            linguistics {
                                tokens: alternatives
                            }
                        }
                    }
                }""";
        var logger = new DeployLoggerStub();
        ApplicationBuilder builder = new ApplicationBuilder(logger);
        builder.addSchema(schema);
        builder.build(true);

        assertEquals(List.of("For schema 'doc', field 's1': tokens is set for indexing but not for searching, " +
                             "which will use the stemming setting instead. This may lead to recall issues.",
                             "For schema 'doc', field 's2': tokens is set for searching but not for indexing, " +
                             "which will use the stemming setting instead. This may lead to recall issues."),
                     logger.entries.stream().map(entry -> entry.message).toList());
    }

    @Test
    void testIllegalLinguisticsTokens() throws Exception {
        String schema =
                """
                schema doc {

                    document doc {

                        field s1 type string {
                            indexing: index
                            linguistics {
                                tokens: shortest
                            }
                        }
                    }
                }""";
        try {
            ApplicationBuilder builder = new ApplicationBuilder(new DeployLoggerStub());
            builder.addSchema(schema);
            builder.build(true);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("'shortest' is not a valid tokens setting"),
                       "Unexpected message: " + e.getMessage());
        }
    }

    @Test
    void testDuplicateLinguisticsTokensIsIllegal() throws Exception {
        String schema =
                """
                schema doc {

                    document doc {

                        field s1 type string {
                            indexing: index
                            linguistics {
                                tokens: alternatives
                                tokens: original
                            }
                        }
                    }
                }""";
        try {
            ApplicationBuilder builder = new ApplicationBuilder(new DeployLoggerStub());
            builder.addSchema(schema);
            builder.build(true);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("linguistics 's1' error: already has tokens alternatives"),
                       "Unexpected message: " + e.getMessage());
        }
    }

    @Test
    void testDuplicateLinguisticsProfileIsIllegal() throws Exception {
        String schema =
                """
                schema doc {

                    document doc {

                        field s1 type string {
                            indexing: index
                            linguistics {
                                profile: p1
                                profile: p2
                            }
                        }
                    }
                }""";
        try {
            ApplicationBuilder builder = new ApplicationBuilder(new DeployLoggerStub());
            builder.addSchema(schema);
            builder.build(true);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("linguistics 's1' error: already has profile p1"),
                       "Unexpected message: " + e.getMessage());
        }
    }

    @Test
    void testRepeatedLinguisticsIndexBlockIsIllegal() throws Exception {
        String schema =
                """
                schema doc {

                    document doc {

                        field s1 type string {
                            indexing: index
                            linguistics {
                                index {
                                    tokens: alternatives
                                }
                                index {
                                    tokens: original
                                }
                            }
                        }
                    }
                }""";
        try {
            ApplicationBuilder builder = new ApplicationBuilder(new DeployLoggerStub());
            builder.addSchema(schema);
            builder.build(true);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("linguistics index 's1' error: is given more than once"),
                       "Unexpected message: " + e.getMessage());
        }
    }

    @Test
    void testDuplicateLinguisticsProfileForIndexIsIllegal() throws Exception {
        String schema =
                """
                schema doc {

                    document doc {

                        field s1 type string {
                            indexing: index
                            linguistics {
                                profile {
                                    index: p1
                                    index: p2
                                }
                            }
                        }
                    }
                }""";
        try {
            ApplicationBuilder builder = new ApplicationBuilder(new DeployLoggerStub());
            builder.addSchema(schema);
            builder.build(true);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("linguistics index 's1' error: already has profile p1"),
                       "Unexpected message: " + e.getMessage());
        }
    }

    @Test
    void testLinguisticsProfileBlockCombinedWithOtherSettings() throws Exception {
        // The profile { index: ... } form defaults the search profile to the index profile
        var field = linguisticsField("linguistics { profile { index: p1 } }");
        assertEquals("p1", field.getIndexLinguisticsProfile());
        assertEquals("p1", field.getSearchLinguisticsProfile());

        // ... but an explicit search profile wins, regardless of the order of the two blocks
        for (var block : List.of("linguistics { profile { index: p1 } search { profile: p3 } }",
                                 "linguistics { search { profile: p3 } profile { index: p1 } }")) {
            field = linguisticsField(block);
            assertEquals("p1", field.getIndexLinguisticsProfile(), block);
            assertEquals("p3", field.getSearchLinguisticsProfile(), block);
        }

        // A search block which sets something else does not stop the defaulting
        field = linguisticsField("linguistics { profile { index: p1 } search { tokens: original } }");
        assertEquals("p1", field.getIndexLinguisticsProfile());
        assertEquals("p1", field.getSearchLinguisticsProfile());
        assertEquals(TokensMode.ORIGINAL, field.getSearchLinguisticsTokens());

        // The profile block is a way of saying the same thing as a flat profile, so combining
        // the two is rejected rather than given a precedence nobody would guess
        for (var block : List.of("linguistics { profile: p1 profile { index: p2 } }",
                                 "linguistics { profile { index: p2 } profile: p1 }",
                                 "linguistics { profile { index: p2 } index { profile: p1 } }")) {
            var e = assertThrows(IllegalArgumentException.class, () -> linguisticsField(block), block);
            assertTrue(e.getMessage().contains("cannot set both a profile and a profile block") ||
                       e.getMessage().contains("already has profile"),
                       "Unexpected message for " + block + ": " + e.getMessage());
        }
    }

    @Test
    void testRepeatedLinguisticsBlockDoesNotClearEarlierSettings() throws Exception {
        // A second linguistics block used to silently null out what the first one set
        var field = linguisticsField("linguistics { profile: p1 }\n                            linguistics { tokens: original }");
        assertEquals("p1", field.getIndexLinguisticsProfile());
        assertEquals("p1", field.getSearchLinguisticsProfile());
        assertEquals(TokensMode.ORIGINAL, field.getIndexLinguisticsTokens());

        var e = assertThrows(IllegalArgumentException.class,
                             () -> linguisticsField("linguistics { profile: p1 }\n                            linguistics { profile: p2 }"));
        assertTrue(e.getMessage().contains("already has linguistics profile for index"),
                   "Unexpected message: " + e.getMessage());
    }

    /** Returns the 's1' field of a schema having the given linguistics block on it */
    private ImmutableSDField linguisticsField(String linguistics) throws Exception {
        String schema =
                """
                schema doc {
                    document doc {
                        field s1 type string {
                            indexing: index
                            %s
                        }
                    }
                }""".formatted(linguistics);
        ApplicationBuilder builder = new ApplicationBuilder(new DeployLoggerStub());
        builder.addSchema(schema);
        return builder.build(true).schemas().get("doc").getField("s1");
    }

    @Test
    void testCasedWordMatching() throws Exception {
        String schema =
                """
                schema doc {

                    document doc {

                        field myString type string {
                            indexing: index
                            match {
                                word
                                cased
                            }
                        }
                    }
                }""";
        ApplicationBuilder builder = new ApplicationBuilder(new DeployLoggerStub());
        builder.addSchema(schema);
        var application = builder.build(true);
        var derived = new DerivedConfiguration(application.schemas().get("doc"), application.rankProfileRegistry());

        var ilConfigBuilder = new IlscriptsConfig.Builder();
        derived.getIndexingScript().getConfig(ilConfigBuilder);
        assertEquals("clear_state | guard { input myString | exact keep-case | index myString; }",
                     ilConfigBuilder.build().ilscript().get(0).content(0));


        var indexInfoConfigBuilder = new IndexInfoConfig.Builder();
        derived.getIndexInfo().getConfig(indexInfoConfigBuilder);
        assertFalse(indexInfoConfigBuilder.build().toString().contains("lowercase"));
    }

    /** Should not cause a cycle detection false positive by mistaking the attribute argument for a function call. */
    @Test
    void testFieldAndFunctionWithSameName() throws Exception {
        String schema =
                """
                schema doc {

                    document doc {

                        field numerical_1 type double {
                            indexing: attribute
                        }
                
                    }
                
                    rank-profile my_profile inherits default {

                        function numerical_1() {
                            expression: attribute(numerical_1)
                        }

                        first-phase {
                            expression: 1.0
                        }
                    }

                }""";

        ApplicationBuilder builder = new ApplicationBuilder(new DeployLoggerStub());
        builder.addSchema(schema);
        var application = builder.build(true);
        new DerivedConfiguration(application.schemas().get("doc"), application.rankProfileRegistry());
    }

    @Test
    void testDuplicateField() {
        String schema =
                """
                schema doc {

                    document doc {

                        field duplicated type string {
                        }
                
                    }
                
                    field duplicated type string {
                    }

                }""";

        try {
            ApplicationBuilder builder = new ApplicationBuilder(new DeployLoggerStub());
            builder.addSchema(schema);
            fail("Should have failed");
        }
        catch (Exception e) {
            assertEquals("schema 'doc' error: duplicate field 'duplicated': Also defined as a document field",
                         e.getMessage());
        }
    }

    @Test
    void testInnerRankProfiles() throws Exception {
        String schema =
                """
                schema doc {
                    document doc {
                    }
                    rank-profile outer {
                        first-phase {
                            expression: 1
                        }
                        rank-profile inner1 inherits outer {
                            first-phase {
                                expression: 2
                            }
                        }
                        rank-profile inner2 inherits outer {
                            first-phase {
                                expression: 3
                            }
                            rank-profile grandinner inherits inner2 {
                                first-phase {
                                    expression: 4
                                }
                                rank-profile grandgrandinner inherits grandinner {
                                    first-phase {
                                        expression: 5
                                    }
                                }
                            }
                        }
                    }
                }""";
        ApplicationBuilder builder = new ApplicationBuilder(new DeployLoggerStub());
        builder.addSchema(schema);
        var application = builder.build(true);
        new DerivedConfiguration(application.schemas().get("doc"), application.rankProfileRegistry());
        assertEquals(7, application.rankProfileRegistry().rankProfilesOf(application.schemas().get("doc")).size());

        var inner1 = application.rankProfileRegistry().get("doc", "outer.inner1");
        assertNotNull(inner1);
        assertEquals("outer.inner1", inner1.name());
        assertEquals("2", inner1.getFirstPhase().function().getBody().getRoot().toString());

        var inner2 = application.rankProfileRegistry().get("doc", "outer.inner2");
        assertNotNull(inner2);
        assertEquals("outer.inner2", inner2.name());
        assertEquals("3", inner2.getFirstPhase().function().getBody().getRoot().toString());

        var grandinner = application.rankProfileRegistry().get("doc", "outer.inner2.grandinner");
        assertNotNull(grandinner);
        assertEquals("outer.inner2.grandinner", grandinner.name());
        assertEquals("4", grandinner.getFirstPhase().function().getBody().getRoot().toString());

        var grandgrandinner = application.rankProfileRegistry().get("doc", "outer.inner2.grandinner.grandgrandinner");
        assertNotNull(grandgrandinner);
        assertEquals("outer.inner2.grandinner.grandgrandinner", grandgrandinner.name());
        assertEquals("5", grandgrandinner.getFirstPhase().function().getBody().getRoot().toString());
    }

    @Test
    void testInnerRankProfileMustInheritOuter() throws Exception {
        String schema =
                """
                schema doc {
                    document doc {
                    }
                    rank-profile outer {
                        rank-profile inner1 { # missing inherits outer
                        }
                    }
                }""";
        try {
            ApplicationBuilder builder = new ApplicationBuilder(new DeployLoggerStub());
            builder.addSchema(schema);
            var application = builder.build(true);
            new DerivedConfiguration(application.schemas().get("doc"), application.rankProfileRegistry());
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("In rank-profile 'inner1': Inner profile 'inner1' must inherit 'outer'",
                         Exceptions.toMessageString(e));
        }
    }

    @Test
    void testInnerProfilesWithDuplicateNamesAreAllowed() throws ParseException {
        String schema =
                """
                schema doc {
                    document doc {}
                    rank-profile ranking_a {
                        rank-profile debug inherits ranking_a {}
                    }
                    rank-profile ranking_b {
                        rank-profile debug inherits ranking_b {}
                    }
                }""";
        ApplicationBuilder builder = new ApplicationBuilder(new DeployLoggerStub());
        builder.addSchema(schema);
        var application = builder.build(true);
        new DerivedConfiguration(application.schemas().get("doc"), application.rankProfileRegistry());
    }

    @Test
    void testRankSettingsToSchemaInfo() throws Exception {
        String schema =
                """
                schema doc {
                    document doc {
                        field test type int {
                            indexing: attribute
                            attribute: fast-search
                        }
                    }
                    rank-profile test {
                        match-phase {
                            attribute: test
                            max-hits: 456
                        }
                        first-phase {
                            keep-rank-count: 1234
                        }
                        second-phase {
                            rerank-count: 43
                        }
                        function foo() {
                            expression: attribute(test)
                        }
                        sort-features {
                            foo
                        }
                    }
                }""";
        ApplicationBuilder builder = new ApplicationBuilder(new DeployLoggerStub());
        builder.addSchema(schema);
        var application = builder.build(true);
        var derived = new DerivedConfiguration(application.schemas().get("doc"), application.rankProfileRegistry());
        var schemaInfoConfigBuilder = new SchemaInfoConfig.Builder();
        derived.getSchemaInfo().getConfig(schemaInfoConfigBuilder);
        var schemaInfoConfig = schemaInfoConfigBuilder.build();
        var schemaInfoConfigText = schemaInfoConfig.toString();
        assertTrue(schemaInfoConfigText.contains("rerankCount 43"));
        assertTrue(schemaInfoConfigText.contains("keepRankCount 1234"));
        assertTrue(schemaInfoConfigText.contains("matchPhaseMaxHits 456"));
        var testProfile = schemaInfoConfig.schema().get(0).rankprofile().stream()
                .filter(profile -> profile.name().equals("test"))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("foo"), testProfile.sortFeature());
        assertFalse(schemaInfoConfigText.contains("rankingExpression(foo)"));
    }

    @Test
    void testTotalRankSettingsToSchemaInfo() throws Exception {
        String schema =
                """
                schema doc {
                    document doc {
                        field test type int {
                            indexing: attribute
                            attribute: fast-search
                        }
                    }
                    rank-profile test {
                        match-phase {
                            attribute: test
                            total-max-hits: 4567
                        }
                        first-phase {
                            total-keep-rank-count: 2345
                        }
                        second-phase {
                            total-rerank-count: 213
                        }
                    }
                }""";
        ApplicationBuilder builder = new ApplicationBuilder(new DeployLoggerStub());
        builder.addSchema(schema);
        var application = builder.build(true);
        var derived = new DerivedConfiguration(application.schemas().get("doc"), application.rankProfileRegistry());
        var schemaInfoConfigBuilder = new SchemaInfoConfig.Builder();
        derived.getSchemaInfo().getConfig(schemaInfoConfigBuilder);
        var schemaInfoConfig = schemaInfoConfigBuilder.build().toString();
        assertTrue(schemaInfoConfig.contains("totalRerankCount 213"));
        assertTrue(schemaInfoConfig.contains("totalKeepRankCount 2345"));
        assertTrue(schemaInfoConfig.contains("totalMatchPhaseMaxHits 4567"));
    }

    @Test
    void testDocumentIdAttributeToSchema() throws Exception {
        String schema_default =
                """
                schema doc {
                    document doc {
                    }
                }""";
        assertFalse(documentIdAttributeEnabled(schema_default));

        String schema_fromdisk =
                """
                schema doc {
                    documentid: from-disk
                    document doc {
                    }
                }""";
        assertFalse(documentIdAttributeEnabled(schema_fromdisk));

        String schema_attribute =
                """
                schema doc {
                    documentid: attribute
                    document doc {
                    }
                }""";
        assertTrue(documentIdAttributeEnabled(schema_attribute));
    }

    private boolean documentIdAttributeEnabled(String schema_string) throws Exception {
        ApplicationBuilder builder = new ApplicationBuilder(new DeployLoggerStub());
        builder.addSchema(schema_string);
        var application = builder.build(true);
        var schema = application.schemas().get("doc");
        return schema.documentIdAttributeEnabled();
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
        assertTrue(schema.documentIdAttributeEnabled());
        assertTrue(schema.isRawAsBase64());
    }

    @Test
    void testLinguisticsTokensIsIgnoredWhenAlternativesAreImpossible() throws Exception {
        // Word, exact and gram matching, and uri fields, cannot produce alternatives to the
        // original token: WordMatch, ExactMatch, NGramMatch and UriHack all force stemming to
        // NONE for them. A tokens setting must not be able to sneak stemming back in on the
        // query side only, which would give zero recall for any stemmable term.
        String schema =
                """
                schema doc {

                    document doc {

                        field s1 type string {
                            indexing: index
                            match: word
                            linguistics { tokens: alternatives }
                        }
                        field s2 type string {
                            indexing: index
                            match: exact
                            linguistics { tokens: alternatives }
                        }
                        field s3 type string {
                            indexing: index
                            match {
                                gram
                                gram-size: 3
                            }
                            linguistics { tokens: alternatives }
                        }
                        field s4 type uri {
                            indexing: index
                            linguistics { tokens: alternatives }
                        }
                        field s5 type int {
                            indexing: attribute
                            linguistics { tokens: alternatives }
                        }
                    }
                }""";
        var logger = new DeployLoggerStub();
        ApplicationBuilder builder = new ApplicationBuilder(logger);
        builder.addSchema(schema);
        var application = builder.build(true);
        var derived = new DerivedConfiguration(application.schemas().get("doc"), application.rankProfileRegistry());

        var indexInfoConfigBuilder = new IndexInfoConfig.Builder();
        derived.getIndexInfo().getConfig(indexInfoConfigBuilder);
        var config = indexInfoConfigBuilder.build();
        for (String field : List.of("s1", "s2", "s3", "s4")) {
            assertNoCommand(field, "stem:ALL_STEMS", config);
            assertNoCommand(field, "stem:BEST", config);
        }
        assertCommand("s1", "word", config);

        // The setting is cleared, so every place resolving stemming agrees
        var doc = application.schemas().get("doc");
        for (String field : List.of("s1", "s2", "s3", "s4", "s5")) {
            assertNull(doc.getField(field).getIndexLinguisticsTokens(), field);
            assertNull(doc.getField(field).getSearchLinguisticsTokens(), field);
        }

        assertEquals(List.of("For schema 'doc', field 's1': tokens is set in the linguistics block, but matching is word, which produces a single token, so it is ignored.",
                             "For schema 'doc', field 's2': tokens is set in the linguistics block, but matching is exact, which produces a single token, so it is ignored.",
                             "For schema 'doc', field 's3': tokens is set in the linguistics block, but matching is gram, which produces a single token, so it is ignored.",
                             "For schema 'doc', field 's4': tokens is set in the linguistics block, but this is a uri field, which is not tokenized by the linguistics implementation, so it is ignored.",
                             "For schema 'doc', field 's5': tokens is set in the linguistics block, but this is not a string field, so it is ignored."),
                     logger.entries.stream().map(entry -> entry.message)
                                            .filter(message -> message.contains("tokens is set in the linguistics block"))
                                            .toList());
    }

    @Test
    void testLinguisticsTokensOverridesIndexStemming() throws Exception {
        // An index level stemming setting loses to the tokens setting of the field, as the field
        // level stemming setting does. Say so rather than dropping it silently.
        String schema =
                """
                schema doc {

                    document doc {

                        field s1 type string {
                            indexing: index
                            linguistics { tokens: alternatives }
                        }
                    }
                    index s1 {
                        stemming: none
                    }
                }""";
        var logger = new DeployLoggerStub();
        ApplicationBuilder builder = new ApplicationBuilder(logger);
        builder.addSchema(schema);
        var application = builder.build(true);
        var derived = new DerivedConfiguration(application.schemas().get("doc"), application.rankProfileRegistry());

        var indexInfoConfigBuilder = new IndexInfoConfig.Builder();
        derived.getIndexInfo().getConfig(indexInfoConfigBuilder);
        assertCommand("s1", "stem:ALL_STEMS", indexInfoConfigBuilder.build());
        assertEquals(List.of("For schema 'doc', field 's1': stemming: none of index 's1' is ignored " +
                             "because this field sets tokens in its linguistics block."),
                     logger.entries.stream().map(entry -> entry.message).toList());
    }

    @Test
    void testNoWarningWhenStemmingAgreesWithLinguisticsTokens() throws Exception {
        // A stemming setting saying the same as tokens is redundant, not ignored: no warning
        assertEquals(List.of(), linguisticsWarnings("""
                field s1 type string {
                    indexing: index
                    stemming: none
                    linguistics { tokens: original }
                }
                """, """
                index s1 {
                    stemming: none
                }
                """));
        // ... which is decided per side when tokens differ between indexing and searching
        assertEquals(List.of("For schema 'doc', field 's1': stemming: none is ignored when searching " +
                             "because this field sets tokens in its linguistics block."),
                     linguisticsWarnings("""
                field s1 type string {
                    indexing: index
                    stemming: none
                    linguistics {
                        index { tokens: original }
                        search { tokens: alternatives }
                    }
                }
                """, ""));
        // An index level setting agreeing with tokens is not warned about either
        assertEquals(List.of(), linguisticsWarnings("""
                field s1 type string {
                    indexing: index
                    linguistics { tokens: alternatives }
                }
                """, """
                index s1 {
                    stemming: all-stems
                }
                """));
    }

    /** Returns the warnings from building a schema 'doc' with the given fields in its document, and the given content outside it */
    private List<String> linguisticsWarnings(String documentFields, String schemaContent) throws Exception {
        String schema =
                """
                schema doc {
                    document doc {
                        %s
                    }
                    %s
                }""".formatted(documentFields, schemaContent);
        var logger = new DeployLoggerStub();
        ApplicationBuilder builder = new ApplicationBuilder(logger);
        builder.addSchema(schema);
        builder.build(true);
        return logger.entries.stream().map(entry -> entry.message).toList();
    }

    @Test
    void testImportedFieldWithLinguisticsTokensOnTargetIsNotStemmed() throws Exception {
        // An imported field is an attribute and is never stemmed, whatever its target says.
        // Its tokens getters delegate to the target, so every place resolving stemming must
        // check for an imported field first: resolving stemming for one is not supported.
        String parent =
                """
                schema parent {
                    document parent {
                        field name type string {
                            indexing: attribute
                            match: text  # keep tokens: implicit word matching would clear it
                            linguistics { tokens: alternatives }
                        }
                    }
                }""";
        String child =
                """
                schema child {
                    document child {
                        field parent_ref type reference<parent> {
                            indexing: attribute
                        }
                        field title type string {
                            indexing: index
                        }
                    }
                    import field parent_ref.name as imported_name {}
                    fieldset fs {
                        fields: title, imported_name
                    }
                }""";
        ApplicationBuilder builder = new ApplicationBuilder(new DeployLoggerStub());
        builder.addSchema(parent);
        builder.addSchema(child);
        var application = builder.build(true);
        var childSchema = application.schemas().get("child");
        var importedName = childSchema.getField("imported_name");
        assertTrue(importedName.isImportedField());
        assertEquals(TokensMode.ALTERNATIVES, importedName.getSearchLinguisticsTokens());
        assertThrows(UnsupportedOperationException.class, () -> importedName.getEffectiveSearchStemming(childSchema));

        var derived = new DerivedConfiguration(childSchema, application.rankProfileRegistry());
        var indexInfoConfigBuilder = new IndexInfoConfig.Builder();
        derived.getIndexInfo().getConfig(indexInfoConfigBuilder);
        var config = indexInfoConfigBuilder.build();
        assertNoCommand("imported_name", "stem:ALL_STEMS", config);
        assertNoCommand("imported_name", "stem:BEST", config);
        assertCommand("fs", "stem:BEST", config);
    }

    @Test
    void testIndexLevelStemmingAppliesEquallyToFieldsAndFieldsets() throws Exception {
        // The stem command of a fieldset must agree with that of its fields, whichever way the
        // stemming of those fields was set. Both go through ImmutableSDField.getEffectiveSearchStemming.
        String schema =
                """
                schema doc {

                    document doc {

                        field s1 type string {
                            indexing: index
                        }
                        field s2 type string {
                            indexing: index
                            index {
                                stemming: shortest
                            }
                        }
                        field s3 type string {
                            indexing: index
                            stemming: none
                            index {
                                stemming: best
                            }
                        }
                    }
                    index s1 {
                        stemming: shortest
                    }
                    fieldset shortest {
                        fields: s1, s2
                    }
                    fieldset none {
                        fields: s3
                    }
                }""";
        ApplicationBuilder builder = new ApplicationBuilder(new DeployLoggerStub());
        builder.addSchema(schema);
        var application = builder.build(true);
        var derived = new DerivedConfiguration(application.schemas().get("doc"), application.rankProfileRegistry());

        var indexInfoConfigBuilder = new IndexInfoConfig.Builder();
        derived.getIndexInfo().getConfig(indexInfoConfigBuilder);
        var config = indexInfoConfigBuilder.build();
        // A schema level index block counts as much as one in the field, for fields and fieldsets alike
        assertCommand("s1", "stem:SHORTEST", config);
        assertCommand("s2", "stem:SHORTEST", config);
        assertCommand("shortest", "stem:SHORTEST", config);
        assertNoCommand("shortest", "stem:BEST", config);
        // An index level setting cannot turn stemming on for a field which has it off
        assertNoCommand("s3", "stem:BEST", config);
        assertNoCommand("none", "stem:BEST", config);
    }

    @Test
    void testOnlyStemmableFieldsDecideTheStemCommandOfAFieldset() throws Exception {
        // A field which cannot be stemmed must not decide the stem command of a fieldset it is in.
        // FieldSetSettings skips such fields when checking that the fields of a fieldset agree on
        // what the query side should do, so if one of them could still win here, index-info would
        // emit a stem command which that check never validated, and which contradicts the tokens
        // setting of the field which does decide it.
        //
        // The fields of a fieldset are ordered by Field.compareTo, which compares the hash of the
        // field name, so which field is seen last is a property of the names: 'n26' is a name which
        // sorts after 's1' and therefore reproduces this, while for instance 'i1' does not.
        for (String nonStemmableField : List.of("n26", "i1")) {
            String schema =
                    """
                    schema doc {

                        document doc {

                            field %s type int {
                                indexing: attribute
                            }
                            field s1 type string {
                                indexing: index
                                linguistics { tokens: alternatives }
                            }
                        }
                        fieldset fs {
                            fields: %s, s1
                        }
                    }""".formatted(nonStemmableField, nonStemmableField);
            ApplicationBuilder builder = new ApplicationBuilder(new DeployLoggerStub());
            builder.addSchema(schema);
            var application = builder.build(true);
            var derived = new DerivedConfiguration(application.schemas().get("doc"), application.rankProfileRegistry());

            var indexInfoConfigBuilder = new IndexInfoConfig.Builder();
            derived.getIndexInfo().getConfig(indexInfoConfigBuilder);
            var config = indexInfoConfigBuilder.build();
            assertCommand("s1", "stem:ALL_STEMS", config);
            assertCommand("fs", "stem:ALL_STEMS", config);
            assertNoCommand("fs", "stem:BEST", config);
        }
    }

    @Test
    void testFieldsetOfNonStemmableFieldsOnlyGetsNoStemCommand() throws Exception {
        // With no stemmable field there is nothing to tell the query side to stem
        String schema =
                """
                schema doc {

                    document doc {

                        field i1 type int {
                            indexing: attribute
                        }
                        field s1 type string {
                            indexing: index
                            match: word
                        }
                    }
                    fieldset fs {
                        fields: i1, s1
                    }
                }""";
        ApplicationBuilder builder = new ApplicationBuilder(new DeployLoggerStub());
        builder.addSchema(schema);
        var application = builder.build(true);
        var derived = new DerivedConfiguration(application.schemas().get("doc"), application.rankProfileRegistry());

        var indexInfoConfigBuilder = new IndexInfoConfig.Builder();
        derived.getIndexInfo().getConfig(indexInfoConfigBuilder);
        var config = indexInfoConfigBuilder.build();
        for (var stemMode : List.of("NONE", "BEST", "SHORTEST", "ALL_STEMS", "ALL"))
            assertNoCommand("fs", "stem:" + stemMode, config);
    }

    private void assertCommand(String field, String command, IndexInfoConfig config) {
        assertTrue(containsCommand(field, command, config), "Field '" + field + "' has '" + command + "'");
    }

    private void assertNoCommand(String field, String command, IndexInfoConfig config) {
        assertFalse(containsCommand(field, command, config), "Field '" + field + "' does not have '" + command + "'");
    }

    private boolean containsCommand(String field, String command, IndexInfoConfig config) {
        if (config.indexinfo().size() != 1) throw new IllegalArgumentException("Support multiple doc types");
        for (var commandConfig : config.indexinfo(0).command()) {
            if (commandConfig.indexname().equals(field) && commandConfig.command().equals(command))
                return true;
        }
        return false;
    }

}
