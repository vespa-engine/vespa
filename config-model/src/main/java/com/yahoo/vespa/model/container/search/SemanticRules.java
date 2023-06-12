// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.io.IOUtils;
import com.yahoo.io.reader.NamedReader;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.prelude.semantics.RuleImporter;
import com.yahoo.prelude.semantics.SemanticRulesConfig;
import com.yahoo.prelude.semantics.parser.ParseException;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Returns the semantic rules config from a set of rule bases.
 * Owned by a container cluster
 *
 * @author bratseth
 */
public class SemanticRules implements Serializable, SemanticRulesConfig.Producer {

    private final List<RuleBase> ruleBases;

    public SemanticRules(List<RuleBase> ruleBases) {
        this.ruleBases = ruleBases;
    }

    @Override
    public void getConfig(SemanticRulesConfig.Builder builder) {
        for (RuleBase ruleBase : ruleBases)
            builder.rulebase(ruleBase.getConfig());
    }

    /** A config view of a rule base */
    public static class RuleBase {

        private final String name;
        private final boolean isDefault;
        private final String rules;

        public RuleBase(String name, boolean isDefault, String rules) {
            this.name = name;
            this.isDefault = isDefault;
            this.rules = rules;
        }

        private SemanticRulesConfig.Rulebase.Builder getConfig() {
            SemanticRulesConfig.Rulebase.Builder ruleBaseBuilder = new SemanticRulesConfig.Rulebase.Builder();
            ruleBaseBuilder.name(name);
            ruleBaseBuilder.isdefault(isDefault);
            ruleBaseBuilder.rules(rules);
            return ruleBaseBuilder;
        }

    }

    public static class SemanticRuleBuilder {

        /** Builds the semantic rules for an application package and validates them */
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

        private RuleBase toRuleBaseConfigView(NamedReader reader) {
            try {
                String ruleBaseString = IOUtils.readAll(reader.getReader());
                boolean isDefault = ruleBaseString.contains("@default");
                return new RuleBase(toName(reader.getName()), isDefault, ruleBaseString);
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

        static Map<String, com.yahoo.prelude.semantics.RuleBase> toMap(SemanticRulesConfig config) throws ParseException, IOException {
            RuleImporter ruleImporter = new RuleImporter(config, true, new SimpleLinguistics());
            Map<String, com.yahoo.prelude.semantics.RuleBase> ruleBaseMap = new HashMap<>();
            for (SemanticRulesConfig.Rulebase ruleBaseConfig : config.rulebase()) {
                com.yahoo.prelude.semantics.RuleBase ruleBase = ruleImporter.importConfig(ruleBaseConfig);
                if (ruleBaseConfig.isdefault())
                    ruleBase.setDefault(true);
                ruleBaseMap.put(ruleBase.getName(), ruleBase);
            }
            return ruleBaseMap;
        }

    }

}
