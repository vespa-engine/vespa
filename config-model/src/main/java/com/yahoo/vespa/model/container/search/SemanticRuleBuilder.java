// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.io.IOUtils;
import com.yahoo.io.reader.NamedReader;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.prelude.semantics.RuleBase;
import com.yahoo.prelude.semantics.RuleImporter;
import com.yahoo.prelude.semantics.SemanticRulesConfig;
import com.yahoo.prelude.semantics.parser.ParseException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the semantic rules from the application package by delegating to SemanticRules.
 *
 * @author bratseth
 */
// TODO: Move into SemanticRules
public class SemanticRuleBuilder {

    /** Build the set of semantic rules for an application package and validates them */
    public SemanticRules build(ApplicationPackage applicationPackage) {
        var ruleFiles = applicationPackage.getFiles(ApplicationPackage.RULES_DIR, "sr");
        var rules = new SemanticRules(ruleFiles.stream().map(this::toRuleBaseConfigView).toList());

        // Create config to make sure rules are valid, config is validated in call to toMap() below
        var builder = new SemanticRulesConfig.Builder();
        rules.getConfig(builder);
        SemanticRulesConfig config = builder.build();
        try {
            toMap(config);  // validates config
            ensureZeroOrOneDefaultRule(config);
        } catch (ParseException | IOException e) {
            throw new RuntimeException(e);
        }
        return rules;
    }

    private SemanticRules.RuleBase toRuleBaseConfigView(NamedReader reader) {
        try {
            String ruleBaseString = IOUtils.readAll(reader.getReader());
            boolean isDefault = ruleBaseString.contains("@default");
            return new SemanticRules.RuleBase(toName(reader.getName()), isDefault, ruleBaseString);
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Could not load rules bases", e);
        }
    }

    private String toName(String fileName) {
        String shortName = new File(fileName).getName();
        return shortName.substring(0, shortName.length() - ".sr".length());
    }

    private void ensureZeroOrOneDefaultRule(SemanticRulesConfig config) {
        String defaultName = null;
        for (SemanticRulesConfig.Rulebase ruleBase : config.rulebase()) {
            if (defaultName != null && ruleBase.isdefault()) {
                List<String> defaultRules = new ArrayList<>(List.of(defaultName, ruleBase.name()));
                defaultRules.sort(String::compareTo);
                throw new IllegalArgumentException("Rules " + defaultRules + " are both marked as the default rule, there can only be one");
            }
            if (ruleBase.isdefault())
                defaultName = ruleBase.name();
        }
    }

    static Map<String, RuleBase> toMap(SemanticRulesConfig config) throws ParseException, IOException {
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
