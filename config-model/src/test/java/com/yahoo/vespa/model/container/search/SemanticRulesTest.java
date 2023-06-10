// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search;

import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.prelude.semantics.RuleBase;
import com.yahoo.prelude.semantics.RuleImporter;
import com.yahoo.prelude.semantics.SemanticRulesConfig;
import com.yahoo.prelude.semantics.parser.ParseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author bratseth
 */
public class SemanticRulesTest {

    private final static String root = "src/test/java/com/yahoo/vespa/model/container/search/semanticrules";

    @Test
    void semanticRulesTest()  throws ParseException, IOException {
        SemanticRuleBuilder ruleBuilder = new SemanticRuleBuilder();
        SemanticRules rules = ruleBuilder.build(FilesApplicationPackage.fromFile(new File(root)));
        SemanticRulesConfig.Builder configBuilder = new SemanticRulesConfig.Builder();
        rules.getConfig(configBuilder);
        SemanticRulesConfig config = new SemanticRulesConfig(configBuilder);
        Map<String, RuleBase> ruleBases = toMap(config);
        assertEquals(2, ruleBases.size());
        assertTrue(ruleBases.containsKey("common"));
        assertTrue(ruleBases.containsKey("other"));
        assertFalse(ruleBases.get("common").isDefault());
        assertTrue(ruleBases.get("other").isDefault());
    }

    private static Map<String, RuleBase> toMap(SemanticRulesConfig config) throws ParseException, IOException {
        RuleImporter ruleImporter = new RuleImporter(config, new SimpleLinguistics());
        Map<String, RuleBase> ruleBaseMap = new HashMap<>();
        for (SemanticRulesConfig.Rulebase ruleBaseConfig : config.rulebase()) {
            RuleBase ruleBase = ruleImporter.importConfig(ruleBaseConfig);
            if (ruleBaseConfig.isdefault())
                ruleBase.setDefault(true);
            ruleBaseMap.put(ruleBase.getName(), ruleBase);
        }
        return ruleBaseMap;
    }

}
