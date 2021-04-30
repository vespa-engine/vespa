// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change.search;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;
import com.yahoo.vespa.model.application.validation.change.VespaConfigChangeAction;
import com.yahoo.vespa.model.application.validation.change.VespaReindexAction;
import org.junit.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class IndexingScriptChangeValidatorTest {

    private static class Fixture extends ContentClusterFixture {
        IndexingScriptChangeValidator validator;

        public Fixture(String currentSd, String nextSd) throws Exception {
            super(currentSd, nextSd);
            validator = new IndexingScriptChangeValidator(ClusterSpec.Id.from("test"),
                                                          currentDb().getDerivedConfiguration().getSearch(),
                                                          nextDb().getDerivedConfiguration().getSearch());
        }

        @Override
        public List<VespaConfigChangeAction> validate() {
            return validator.validate();
        }
    }

    private static class ScriptFixture {

        private final ScriptExpression currentScript;
        private final ScriptExpression nextScript;

        public ScriptFixture(String currentScript, String nextScript) throws Exception {
            this.currentScript = ScriptExpression.fromString(currentScript);
            this.nextScript = ScriptExpression.fromString(nextScript);
        }

        public boolean validate() {
            return IndexingScriptChangeValidator.equalScripts(currentScript, nextScript);
        }
    }

    private static final String FIELD = "field f1 type string";
    private static final String FIELD_F2 = "field f2 type string";

    private static VespaConfigChangeAction expectedReindexingAction(String changedMsg, String fromScript, String toScript) {
        return expectedReindexingAction("f1", changedMsg, fromScript, toScript);
    }

    private static VespaConfigChangeAction expectedReindexingAction(String field, String changedMsg, String fromScript, String toScript) {
        return VespaReindexAction.of(ClusterSpec.Id.from("test"),
                                     ValidationId.indexingChange,
                                     "Field '" + field + "' changed: " +
                                    (changedMsg.isEmpty() ? "" : changedMsg + ", ") +
                                    "indexing script: '" + fromScript + "' -> '" + toScript + "'");
    }

    @Test
    public void requireThatAddingIndexAspectRequireReindexing() throws Exception {
        new Fixture(FIELD + " { indexing: summary }",
                    FIELD + " { indexing: index | summary }").
        assertValidation(expectedReindexingAction("add index aspect",
                "{ input f1 | summary f1; }",
                "{ input f1 | tokenize normalize stem:\"BEST\" | index f1 | summary f1; }"));
    }

    @Test
    public void requireThatRemovingIndexAspectRequireReindexing() throws Exception {
        new Fixture(FIELD + " { indexing: index | summary }",
                    FIELD + " { indexing: summary }").
                assertValidation(expectedReindexingAction("remove index aspect",
                        "{ input f1 | tokenize normalize stem:\"BEST\" | index f1 | summary f1; }",
                        "{ input f1 | summary f1; }"));
    }

    @Test
    public void requireThatChangingStemmingRequireReindexing() throws Exception {
        new Fixture(FIELD + " { indexing: index }",
                    FIELD + " { indexing: index \n stemming: none }").
                assertValidation(expectedReindexingAction("stemming: 'best' -> 'none'",
                        "{ input f1 | tokenize normalize stem:\"BEST\" | index f1; }",
                        "{ input f1 | tokenize normalize | index f1; }"));
    }

    @Test
    public void requireThatChangingNormalizingRequireReindexing() throws Exception {
        new Fixture(FIELD + " { indexing: index }",
                    FIELD + " { indexing: index \n normalizing: none }").
                assertValidation(expectedReindexingAction("normalizing: 'ACCENT' -> 'NONE'",
                        "{ input f1 | tokenize normalize stem:\"BEST\" | index f1; }",
                        "{ input f1 | tokenize stem:\"BEST\" | index f1; }"));
    }

    @Test
    public void requireThatChangingMatchingRequireReindexing() throws Exception {
        new Fixture(FIELD + " { indexing: index \n match: exact }",
                    FIELD + " { indexing: index \n match { gram \n gram-size: 3 } }").
                assertValidation(expectedReindexingAction("matching: 'exact' -> 'gram (size 3)', normalizing: 'LOWERCASE' -> 'CODEPOINT'",
                        "{ input f1 | exact | index f1; }",
                        "{ input f1 | ngram 3 | index f1; }"));
    }

    @Test
    public void requireThatSettingDynamicSummaryRequireReindexing() throws Exception {
        new Fixture(FIELD + " { indexing: summary }",
                    FIELD + " { indexing: summary \n summary: dynamic }").
                assertValidation(expectedReindexingAction("summary field 'f1' transform: 'none' -> 'dynamicteaser'",
                        "{ input f1 | summary f1; }",
                        "{ input f1 | tokenize normalize stem:\"BEST\" | summary f1; }"));
    }

    @Test
    public void requireThatMultipleChangesRequireReindexing() throws Exception {
         new Fixture(FIELD + " { indexing: index } " + FIELD_F2 + " { indexing: index }",
                     FIELD + " { indexing: index \n stemming: none } " + FIELD_F2 + " { indexing: index \n normalizing: none }").
                 assertValidation(Arrays.asList(expectedReindexingAction("f1", "stemming: 'best' -> 'none'",
                                 "{ input f1 | tokenize normalize stem:\"BEST\" | index f1; }",
                                 "{ input f1 | tokenize normalize | index f1; }"),
                         expectedReindexingAction("f2", "normalizing: 'ACCENT' -> 'NONE'",
                                 "{ input f2 | tokenize normalize stem:\"BEST\" | index f2; }",
                                 "{ input f2 | tokenize stem:\"BEST\" | index f2; }")));
    }

    @Test
    public void requireThatAddingIndexFieldIsOk() throws Exception {
        new Fixture("", "field f1 type string { indexing: index | summary }").
                assertValidation();
    }

    @Test
    public void requireThatRemovingIndexFieldIsOk() throws Exception {
        new Fixture("field f1 type string { indexing: index | summary }", "").
                assertValidation();
    }

    @Test
    public void requireThatAddingFieldIsOk() throws Exception {
        new Fixture("", FIELD + " { indexing: attribute | summary }").
                assertValidation();
    }

    @Test
    public void requireThatAddingSummaryAspectIsOk() throws Exception {
        new Fixture(FIELD + " { indexing: attribute }",
                    FIELD + " { indexing: attribute | summary }").
                assertValidation();
    }

    @Test
    public void requireThatSettingDynamicSummaryOnIndexFieldIsOk() throws Exception {
        new Fixture(FIELD + " { indexing: index | summary }",
                    FIELD + " { indexing: index | summary \n summary: dynamic }").
                assertValidation();
    }

    @Test
    public void requireThatOutputExpressionsAreIgnoredInAdvancedScript() throws Exception {
        assertTrue(new ScriptFixture("{ input foo | switch { case \"audio\": input bar | index; case \"video\": input baz | index; default: 0 | index; }; }",
                "{ input foo | switch { case \"audio\": input bar | attribute; case \"video\": input baz | attribute; default: 0 | attribute; }; }").
                validate());
    }

}
