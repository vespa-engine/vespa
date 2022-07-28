// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.document.DataType;
import com.yahoo.schema.Index;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.AbstractSchemaTestCase;
import com.yahoo.schema.document.BooleanIndexDefinition;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;
import com.yahoo.vespa.model.container.search.QueryProfiles;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

import static com.yahoo.schema.processing.AssertIndexingScript.assertIndexing;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Simon Thoresen Hult
 */
public class IndexingScriptRewriterTestCase extends AbstractSchemaTestCase {

    @Test
    void testSetLanguageRewriting() {
        assertIndexingScript("{ input test | set_language; }",
                createField("test", DataType.STRING, "{ set_language }"));
    }

    @Test
    void testSummaryRewriting() {
        assertIndexingScript("{ input test | summary test; }",
                createField("test", DataType.STRING, "{ summary }"));
    }

    @Test
    void testDynamicSummaryRewriting() {
        SDField field = createField("test", DataType.STRING, "{ summary }");
        field.addSummaryField(createDynamicSummaryField(field, "dyn"));
        assertIndexingScript("{ input test | tokenize normalize stem:\"BEST\" | summary dyn; }", field);
    }

    @Test
    void testSummaryRewritingWithIndexing() {
        assertIndexingScript("{ input test | tokenize normalize stem:\"BEST\" | summary test | index test; }",
                createField("test", DataType.STRING, "{ summary | index }"));
    }

    @Test
    void testDynamicAndStaticSummariesRewritingWithIndexing() {
        SDField field = createField("test", DataType.STRING, "{ summary | index }");
        field.addSummaryField(createDynamicSummaryField(field, "dyn"));
        field.addSummaryField(createStaticSummaryField(field, "test"));
        field.addSummaryField(createStaticSummaryField(field, "other"));
        field.addSummaryField(createDynamicSummaryField(field, "dyn2"));
        assertIndexingScript("{ input test | tokenize normalize stem:\"BEST\" | summary dyn | summary dyn2 | summary other | " +
                "summary test | index test; }", field);
    }

    @Test
    void testIntSummaryRewriting() {
        assertIndexingScript("{ input test | summary test | attribute test; }",
                createField("test", DataType.INT, "{ summary | index }"));
    }

    @Test
    void testStringAttributeSummaryRewriting() {
        assertIndexingScript("{ input test | summary test | attribute test; }",
                createField("test", DataType.STRING, "{ summary | attribute }"));
    }

    @Test
    void testMultiblockTokenize() {
        SDField field = createField("test", DataType.STRING,
                "{ input test | tokenize | { summary test; }; }");
        assertIndexingScript("{ input test | tokenize | { summary test; }; }", field);
    }

    @Test
    void requireThatOutputDefaultsToCurrentField() {
        assertIndexingScript("{ input test | attribute test; }",
                createField("test", DataType.STRING, "{ attribute; }"));
        assertIndexingScript("{ input test | tokenize normalize stem:\"BEST\" | index test; }",
                createField("test", DataType.STRING, "{ index; }"));
        assertIndexingScript("{ input test | summary test; }",
                createField("test", DataType.STRING, "{ summary; }"));
    }

    @Test
    void testTokenizeComparisonDisregardsConfig() {
        assertIndexingScript("{ input test | tokenize normalize stem:\"BEST\" | summary test | index test; }",
                createField("test", DataType.STRING, "{ summary | tokenize | index; }"));
    }

    @Test
    void testDerivingFromSimple() throws Exception {
        assertIndexing(Arrays.asList("clear_state | guard { input access | attribute access; }",
                        "clear_state | guard { input category | split \";\" | attribute category_arr; }",
                        "clear_state | guard { input category | tokenize | index category; }",
                        "clear_state | guard { input categories_src | lowercase | normalize | tokenize normalize stem:\"BEST\" | index categories; }",
                        "clear_state | guard { input categoriesagain_src | lowercase | normalize | tokenize normalize stem:\"BEST\" | index categoriesagain; }",
                        "clear_state | guard { input chatter | tokenize normalize stem:\"BEST\" | index chatter; }",
                        "clear_state | guard { input description | tokenize normalize stem:\"BEST\" | summary description | summary dyndesc | index description; }",
                        "clear_state | guard { input exactemento_src | lowercase | tokenize normalize stem:\"BEST\" | index exactemento | summary exactemento; }",
                        "clear_state | guard { input longdesc | tokenize normalize stem:\"BEST\" | summary dyndesc2 | summary dynlong | summary longdesc | summary longstat; }",
                        "clear_state | guard { input measurement | attribute measurement | summary measurement; }",
                        "clear_state | guard { input measurement | to_array | attribute measurement_arr; }",
                        "clear_state | guard { input popularity | attribute popularity; }",
                        "clear_state | guard { input popularity * input measurement | attribute popsiness; }",
                        "clear_state | guard { input smallattribute | attribute smallattribute; }",
                        "clear_state | guard { input title | tokenize normalize stem:\"BEST\" | summary title | index title; }",
                        "clear_state | guard { input title . \" \" . input category | tokenize | summary exact | index exact; }"),
                ApplicationBuilder.buildFromFile("src/test/examples/simple.sd"));
    }

    @Test
    void testIndexRewrite() throws Exception {
        assertIndexing(
                Arrays.asList("clear_state | guard { input title_src | lowercase | normalize | " +
                        "                      tokenize | index title; }",
                        "clear_state | guard { input title_src | summary title_s; }"),
                ApplicationBuilder.buildFromFile("src/test/examples/indexrewrite.sd"));
    }

    @Test
    void requireThatPredicateFieldsGetOptimization() {
        assertIndexingScript("{ 10 | set_var arity | { input test | optimize_predicate | attribute test; }; }",
                createPredicateField(
                        "test", DataType.PREDICATE, "{ attribute; }", 10, OptionalLong.empty(), OptionalLong.empty()));
        assertIndexingScript("{ 10 | set_var arity | { input test | optimize_predicate | summary test | attribute test; }; }",
                createPredicateField(
                        "test", DataType.PREDICATE, "{ summary | attribute ; }", 10, OptionalLong.empty(), OptionalLong.empty()));
        assertIndexingScript(
                "{ 2 | set_var arity | 0L | set_var lower_bound | 1023L | set_var upper_bound | " +
                        "{ input test | optimize_predicate | attribute test; }; }",
                createPredicateField("test", DataType.PREDICATE, "{ attribute; }", 2, OptionalLong.of(0L), OptionalLong.of(1023L)));
    }

    private static void assertIndexingScript(String expectedScript, SDField unprocessedField) {
        assertEquals(expectedScript,
                processField(unprocessedField).toString());
    }

    private static ScriptExpression processField(SDField unprocessedField) {
        SDDocumentType sdoc = new SDDocumentType("test");
        sdoc.addField(unprocessedField);
        Schema schema = new Schema("test", MockApplicationPackage.createEmpty());
        schema.addDocument(sdoc);
        new Processing().process(schema, new BaseDeployLogger(), new RankProfileRegistry(),
                                 new QueryProfiles(), true, false, Set.of());
        return unprocessedField.getIndexingScript();
    }

    private static SDField createField(String name, DataType type, String script) {
        SDField field = new SDField(null, name, type);
        field.parseIndexingScript(script);
        return field;
    }

    private static SDField createPredicateField(
            String name, DataType type, String script, int arity, OptionalLong lower_bound, OptionalLong upper_bound) {
        SDField field = new SDField(null, name, type);
        field.parseIndexingScript(script);
        Index index = new Index("foo");
        index.setBooleanIndexDefiniton(new BooleanIndexDefinition(
                OptionalInt.of(arity), lower_bound, upper_bound, OptionalDouble.empty()));
        field.addIndex(index);
        return field;
    }

    private static SummaryField createDynamicSummaryField(SDField field, String name) {
        return createSummaryField(field, name, true);
    }

    private static SummaryField createStaticSummaryField(SDField field, String name) {
        return createSummaryField(field, name, false);
    }

    private static SummaryField createSummaryField(SDField field, String name, boolean dynamic) {
        SummaryField summaryField = new SummaryField(name, field.getDataType());
        if (dynamic) {
            summaryField.setTransform(SummaryTransform.DYNAMICTEASER);
        }
        summaryField.addDestination("default");
        summaryField.addSource(field.getName());
        return summaryField;
    }

}
