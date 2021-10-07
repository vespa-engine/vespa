// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search;

import com.yahoo.prelude.semantics.SemanticRulesConfig;
import java.io.Serializable;
import java.util.List;

/**
 * Returns the semantic rules config form a set of rule bases.
 * Owned by a container cluster
 *
 * @author bratseth
 */
public class SemanticRules implements Serializable, SemanticRulesConfig.Producer {

    private List<RuleBase> ruleBases;

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

}
