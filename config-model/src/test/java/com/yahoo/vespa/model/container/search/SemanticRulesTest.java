// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search;

import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.prelude.semantics.RuleBase;
import com.yahoo.prelude.semantics.SemanticRulesConfig;
import com.yahoo.prelude.semantics.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static com.yahoo.vespa.model.container.search.SemanticRules.SemanticRuleBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author bratseth
 */
public class SemanticRulesTest {

    private static final String basePath = "src/test/java/com/yahoo/vespa/model/container/search/";
    private static final String root = basePath + "semanticrules";
    private static final String rootWithErrors = basePath + "semanticrules_with_errors";
    private static final String rootWithDuplicateDefault = basePath + "semanticrules_with_duplicate_default_rule";

    @Test
    void semanticRulesTest() throws ParseException, IOException {
        SemanticRuleBuilder ruleBuilder = new SemanticRuleBuilder();
        SemanticRules rules = ruleBuilder.build(FilesApplicationPackage.fromFile(new File(root)));
        SemanticRulesConfig.Builder configBuilder = new SemanticRulesConfig.Builder();
        rules.getConfig(configBuilder);
        SemanticRulesConfig config = new SemanticRulesConfig(configBuilder);
        Map<String, RuleBase> ruleBases = SemanticRuleBuilder.toMap(config);
        assertEquals(2, ruleBases.size());
        assertTrue(ruleBases.containsKey("common"));
        assertTrue(ruleBases.containsKey("other"));
        assertFalse(ruleBases.get("common").isDefault());
        assertTrue(ruleBases.get("other").isDefault());
        assertTrue(ruleBases.get("other").includes("common"));
        assertNotNull(ruleBases.get("other").getCondition("stopword"));
    }

    @Test
    void rulesWithErrors() {
        try {
            new SemanticRuleBuilder().build(FilesApplicationPackage.fromFile(new File(rootWithErrors)));
            fail("should fail with exception");
        } catch (Exception e) {
            assertEquals("com.yahoo.prelude.semantics.parser.ParseException: Could not parse rule 'invalid'", e.getMessage());
        }
    }

    @Test
    void rulesWithDuplicateDefault() {
        try {
            new SemanticRuleBuilder().build(FilesApplicationPackage.fromFile(new File(rootWithDuplicateDefault)));
            fail("should fail with exception");
        } catch (Exception e) {
            assertEquals("Rules [one, other] are both marked as the default rule, there can only be one", e.getMessage());
        }
    }

}
