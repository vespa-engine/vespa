// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.language.Linguistics;
import com.yahoo.prelude.ConfigurationException;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.PhaseNames;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.yahoo.prelude.querytransform.StemmingSearcher.STEMMING;

/**
 * Analyzes query semantics and enhances the query to reflect findings
 *
 * @author bratseth
 */
@After(PhaseNames.RAW_QUERY)
@Before({PhaseNames.TRANSFORMED_QUERY, STEMMING})
public class SemanticSearcher extends Searcher {

    private static final CompoundName rulesRulebase = new CompoundName("rules.rulebase");
    private static final CompoundName rulesOff = new CompoundName("rules.off");
    private static final CompoundName tracelevelRules = new CompoundName("tracelevel.rules");

    /** The default rule base of this */
    private RuleBase defaultRuleBase;

    /** All rule bases of this (always including the default) */
    private final Map<String, RuleBase> ruleBases = new java.util.HashMap<>();

    /** Creates a semantic searcher using the given default rule base */
    public SemanticSearcher(RuleBase ruleBase) {
        this(List.of(ruleBase));
        defaultRuleBase = ruleBase;
    }

    public SemanticSearcher(RuleBase ... ruleBases) {
        this(Arrays.asList(ruleBases));
    }

    @Inject
    public SemanticSearcher(SemanticRulesConfig config, Linguistics linguistics) {
        this(toList(config, linguistics));
    }

    public SemanticSearcher(List<RuleBase> ruleBases) {
        for (RuleBase ruleBase : ruleBases) {
            if (ruleBase.isDefault())
                defaultRuleBase = ruleBase;
            this.ruleBases.put(ruleBase.getName(),ruleBase);
        }
    }

    private static List<RuleBase> toList(SemanticRulesConfig config, Linguistics linguistics) {
        try {
            RuleImporter ruleImporter = new RuleImporter(config, linguistics);
            List<RuleBase> ruleBaseList = new java.util.ArrayList<>();
            for (SemanticRulesConfig.Rulebase ruleBaseConfig : config.rulebase()) {
                RuleBase ruleBase = ruleImporter.importConfig(ruleBaseConfig);
                if (ruleBaseConfig.isdefault())
                    ruleBase.setDefault(true);
                ruleBaseList.add(ruleBase);
            }
            return ruleBaseList;
        }
        catch (Exception e) {
            throw new ConfigurationException("Failed configuring semantic rules",e);
        }
    }

    @Override
    public Result search(Query query, Execution execution) {
        if (query.properties().getBoolean(rulesOff))
            return execution.search(query);

        int traceLevel = query.properties().getInteger(tracelevelRules, query.getTrace().getLevel() - 2);
        if (traceLevel < 0) traceLevel = 0;
        RuleBase ruleBase = resolveRuleBase(query);
        if (ruleBase == null)
            return execution.search(query);

        String error = ruleBase.analyze(query, traceLevel);
        if (error != null)
            return handleError(ruleBase, query, error);
        else
            return execution.search(query);
    }

    private RuleBase resolveRuleBase(Query query) {
        String ruleBaseName = query.properties().getString(rulesRulebase);
        if (ruleBaseName == null || ruleBaseName.equals("")) return getDefaultRuleBase();
        RuleBase ruleBase = getRuleBase(ruleBaseName);
        if (ruleBase == null)
            throw new RuleBaseException("Requested rule base '" + ruleBaseName + "' does not exist");
        return ruleBase;
    }

    private Result handleError(RuleBase ruleBase,Query query,String error) {
        String message = "Evaluation of query '" + query.getModel().getQueryTree() +
                         "' over '" + ruleBase + "' caused the invalid query '" +
                         query.getModel().getQueryTree().getRoot() + "': " + error;
        getLogger().warning(message);
        return new Result(query, ErrorMessage.createInvalidQueryTransformation(message));
    }

    /** Returns the default rule base */
    public RuleBase getDefaultRuleBase() { return defaultRuleBase; }

    /**
     * Returns the rule base of the given name, or null if none.
     * The part of the name following the last dot (if any) is removed before lookup.
     */
    public RuleBase getRuleBase(String ruleBaseName) {
        ruleBaseName = RuleImporter.stripLastName(ruleBaseName);
        return ruleBases.get(ruleBaseName);
    }

}
