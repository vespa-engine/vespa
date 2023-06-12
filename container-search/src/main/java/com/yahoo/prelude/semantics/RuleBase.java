// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics;

import com.yahoo.language.Linguistics;
import com.yahoo.prelude.semantics.rule.CompositeCondition;
import com.yahoo.prelude.semantics.rule.Condition;
import com.yahoo.prelude.semantics.rule.NamedCondition;
import com.yahoo.prelude.semantics.rule.ProductionRule;
import com.yahoo.prelude.semantics.rule.SuperCondition;
import com.yahoo.search.Query;
import com.yahoo.prelude.querytransform.PhraseMatcher;
import com.yahoo.prelude.semantics.engine.RuleEngine;
import com.yahoo.prelude.semantics.parser.ParseException;
import com.yahoo.protect.Validator;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * A set of semantic production rules and named conditions used to analyze and rewrite queries
 *
 * @author bratseth
 */
public class RuleBase {

    /** The globally identifying name of this rule base */
    private String name;

    /** The name of the source of this rules */
    private String source;

    /** The name of the automata file used, or null if none */
    private String automataFileName = null;

    /**
     * True if this rule base is default.
     * The semantics of default is left to the surrounding framework
     */
    private boolean isDefault = false;

    private final List<ProductionRule> productionRules = new java.util.ArrayList<>();

    private Map<String, NamedCondition> namedConditions = new java.util.LinkedHashMap<>();

    /** The analyzer used to do evaluations over this rule base */
    private final RuleEngine analyzer = new RuleEngine(this);

    private static final PhraseMatcher nullPhraseMatcher = PhraseMatcher.getNullMatcher();

    /**
     * The matcher using an automata to match terms and phrases prior to matching rules
     * or the null matcher if no matcher is used.
     */
    private PhraseMatcher phraseMatcher = nullPhraseMatcher;

    /**
     * The names of the rule bases included indirectly or directly in this
     * Ordered by first to last included
     */
    private final Set<String> includedNames = new java.util.LinkedHashSet<>();

    /**
     * True if this uses an automata, even if an automata is not present right now. Useful to validate without
     * having automatas available
     */
    private boolean usesAutomata = false;

    /** Creates an empty rule base */
    public RuleBase(String name) {
        this.name = name;
    }

    /**
     * Creates a rule base from file
     *
     * @param  ruleFile the rule file to read. The name of the file (minus path) becomes the rule base name.
     * @param  automataFile the automata file, or null to not use an automata
     * @throws java.io.IOException if there is a problem reading one of the files
     * @throws ParseException if the rule file can not be parsed correctly
     * @throws RuleBaseException if the rule file contains inconsistencies
     */
    public static RuleBase createFromFile(String ruleFile, String automataFile, Linguistics linguistics)
            throws java.io.IOException, ParseException {
        return new RuleImporter(linguistics).importFile(ruleFile, automataFile);
    }

    /**
     * Creates a rule base from a string
     *
     * @param  name the name of the rule base
     * @param  ruleString the rule string to read
     * @param  automataFile the automata file, or null to not use an automata
     * @throws java.io.IOException if there is a problem reading the automata file
     * @throws com.yahoo.prelude.semantics.parser.ParseException if the rule file can not be parsed correctly
     * @throws com.yahoo.prelude.semantics.RuleBaseException if the rule file contains inconsistencies
     */
    public static RuleBase createFromString(String name, String ruleString, String automataFile, Linguistics linguistics)
            throws java.io.IOException, ParseException {
        RuleBase base = new RuleImporter(linguistics).importString(ruleString, automataFile);
        base.setName(name);
        return base;
    }

    /**
     * <p>Include another rule base into this. This <b>transfers ownership</b>
     * of the given rule base - it can not be subsequently used for any purpose
     * (including accessing).</p>
     *
     * <p>Each rule base will only be included by the first include directive encountered
     * for that rule base.</p>
     */
    public void include(RuleBase include) {
        productionRules.add(new IncludeDirective(include));
        includedNames.addAll(include.includedNames);
        includedNames.add(include.getName());
    }

    /** Rules are order based - they are included recursively depth first */
    private void inlineIncluded() {
        // Re-add our own conditions last to - added later overrides
        Map<String, NamedCondition> thisConditions = namedConditions;
        namedConditions = new LinkedHashMap<>();

        Set<RuleBase> included = new HashSet<>();
        included.add(this);
        for (ListIterator<ProductionRule> i = productionRules.listIterator(); i.hasNext(); ) {
            ProductionRule rule = i.next();
            if ( ! (rule instanceof IncludeDirective) ) continue;

            i.remove();
            RuleBase toInclude = ((IncludeDirective)rule).getIncludedBase();
            if ( ! included.contains(toInclude))
                toInclude.inlineIn(this, i, included);
        }

        namedConditions.putAll(thisConditions);
    }

    /**
     * Recursively include this and everything it includes into the given rule base.
     * Skips bases already included in this.
     */
    private void inlineIn(RuleBase receiver, ListIterator<ProductionRule> receiverRules, Set<RuleBase> included) {
        if (included.contains(this)) return;
        included.add(this);

        for (Iterator<ProductionRule> i = productionRules.iterator(); i.hasNext(); ) {
            ProductionRule rule = i.next();
            if (rule instanceof IncludeDirective)
                ((IncludeDirective)rule).getIncludedBase().inlineIn(receiver, receiverRules, included);
            else
                receiverRules.add(rule);
        }

        receiver.namedConditions.putAll(namedConditions);
    }

    /** Adds a named condition which can be referenced by rules */
    public void addCondition(NamedCondition namedCondition) {
        namedConditions.put(namedCondition.getName(), namedCondition);

        Condition condition = namedCondition.getCondition();
        Condition superCondition = findIncludedCondition(namedCondition.getName());
        resolveSuper(condition, superCondition);
    }

    private void resolveSuper(Condition condition, Condition superCondition) {
        if (condition instanceof SuperCondition) {
            ((SuperCondition)condition).setCondition(superCondition);
        }
        else if (condition instanceof CompositeCondition) {
            for (Iterator<Condition> i = ((CompositeCondition)condition).conditionIterator(); i.hasNext(); ) {
                Condition subCondition = i.next();
                resolveSuper(subCondition, superCondition);
            }
        }
    }

    private Condition findIncludedCondition(String name) {
        for (Iterator<ProductionRule> i = productionRules.iterator(); i.hasNext(); ) {
            ProductionRule rule = i.next();
            if ( ! (rule instanceof IncludeDirective) ) continue;

            RuleBase included = ((IncludeDirective)rule).getIncludedBase();
            NamedCondition condition = included.getCondition(name);
            if (condition != null) return condition.getCondition();
            included.findIncludedCondition(name);
        }
        return null;
    }

    /**
     * Returns whether this rule base - directly or through other includes - includes
     * the rule base with the given name
     */
    public boolean includes(String ruleBaseName) {
        return includedNames.contains(ruleBaseName);
    }

    /**
     * Sets the name of this rule base.
     * If this rule base is given to a searcher, it must be removed before the name
     * change, and then re-added
     */
    public void setName(String name) {
        Validator.ensureNotNull("Rule base name", name);
        this.name = name;
    }

    /** Returns the name of this rule base. This is never null. */
    public String getName() { return name; }

    /**
     * Sets the name of the automata file to use as a source of condition matches.
     * To reload the automata, call this again. This can be done safely at any
     * point by any thread while this rule base is in use.
     *
     * @throws IllegalArgumentException if the file is not found
     */
    public void setAutomataFile(String automataFile) {
        if ( ! new File(automataFile).exists())
            throw new IllegalArgumentException("Automata file '" + automataFile + "' " +
                                               "included in " + this + " not found");
        phraseMatcher = new PhraseMatcher(automataFile);
        phraseMatcher.setIgnorePluralForm(true);
        phraseMatcher.setMatchAll(true);
        phraseMatcher.setMatchPhraseItems(true);
        phraseMatcher.setMatchSingleItems(true);
        setPhraseMatcher(phraseMatcher);
        this.automataFileName = automataFile;
    }

    /** Returns the name of the automata file used, or null if none */
    public String getAutomataFile() { return automataFileName; }

    /** Sets whether this base is default, the semantics of default is left to the application */
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }

    /** Returns whether this base is default, the semantics of default is left to the application */
    public boolean isDefault() { return isDefault; }

    /** Thread safely sets the phrase matcher to use in this, or null to not use a phrase matcher */
    public synchronized void setPhraseMatcher(PhraseMatcher matcher) {
        if (matcher == null)
            this.phraseMatcher = nullPhraseMatcher;
        else
            this.phraseMatcher = matcher;
    }

    /** Thread safely gets the phrase matcher to use in this */
    public synchronized PhraseMatcher getPhraseMatcher() {
        return this.phraseMatcher;
    }

    /**
     * The identifying name of the source of this rule base.
     * The absolute file name if this came from a file.
     */
    public String getSource() { return source; }

    /**
     * Sets the name of the source of this rule base. If this came from a file,
     * the source must be set to the absolute file name of the rule base
     */
    public void setSource(String source) { this.source = source; }

    /** Returns whether this uses a phrase matcher automata */
    public boolean usesAutomata() {
        return usesAutomata || phraseMatcher!=nullPhraseMatcher;
    }

    /**
     * Set to true if this uses an automata, even if an automata is not present right now.
     * Useful to validate without having automatas available
     */
    void setUsesAutomata(boolean usesAutomata) { this.usesAutomata = usesAutomata; }

    // Note that included rules are added though a list iterator, not this */
    public void addRule(ProductionRule productionRule) {
        productionRules.add(productionRule);
    }

    /** Returns a named condition, or null if no condition with that name exists */
    public NamedCondition getCondition(String name) {
        return namedConditions.get(name);
    }

    /**
     * Call this when all rules are added, before any rule evaluation starts.
     *
     * @throws RuleBaseException if there is an inconsistency in the rule base.
     */
    public void initialize() {
        inlineIncluded();
        makeReferences();
    }

    /**
     * Analyzes a query over this rule base
     *
     * @param query the query to analyze
     * @param traceLevel the level of tracing to add to the query
     * @return the error caused by analyzing the query, or null if there was no error
     *         If there is an error, this query is destroyed (unusable)
     */
    public String analyze(Query query, int traceLevel) {
        int queryTraceLevel = query.getTrace().getLevel();
        if (traceLevel > 0 && queryTraceLevel == 0)
            query.getTrace().setLevel(1);

        matchAutomata(query, traceLevel);
        String error = analyzer.evaluate(query, traceLevel);

        query.getTrace().setLevel(queryTraceLevel);
        return error;
    }

    protected void matchAutomata(Query query,int traceLevel) {
        List<PhraseMatcher.Phrase> matches = getPhraseMatcher().matchPhrases(query.getModel().getQueryTree().getRoot());
        if (matches == null || matches.size() == 0) return;
        for (Iterator<PhraseMatcher.Phrase> i = matches.iterator(); i.hasNext(); ) {
            PhraseMatcher.Phrase phrase = i.next();
            if (traceLevel >= 3)
                 query.trace("Semantic searcher automata matched " + phrase, false, 1);

            annotatePhrase(phrase, query, traceLevel);
        }
    }

    // TODO: Values are not added right now
    protected void annotatePhrase(PhraseMatcher.Phrase phrase, Query query, int traceLevel) {
        for (StringTokenizer tokens = new StringTokenizer(phrase.getData(), "|", false); tokens.hasMoreTokens(); ) {
            String token = tokens.nextToken();
            int semicolonIndex = token.indexOf(";");
            String annotation = token;
            String value = "";
            if (semicolonIndex > 0) {
                annotation = token.substring(0, semicolonIndex);
                value = token.substring(semicolonIndex + 1);
            }

            // Annotate all matched items
            phrase.getItem(0).addAnnotation(annotation, phrase);
            if (traceLevel >= 4)
                query.trace("   Annotating '" + phrase + "' as " + annotation +
                            (value.equals("") ? "" :"=" + value),false,1);
        }
    }

    private void makeReferences() {
        for (Iterator<ProductionRule> i = ruleIterator(); i.hasNext(); ) {
            ProductionRule rule = i.next();
            rule.makeReferences(this);
        }
        for (Iterator<NamedCondition> i = conditionIterator(); i.hasNext(); ) {
            NamedCondition namedCondition = i.next();
            namedCondition.getCondition().makeReferences(this);
        }
    }

    /** Returns the rules in added order */
    public ListIterator<ProductionRule> ruleIterator() { return productionRules.listIterator(); }

    /** Returns the rules unmodifiable */
    public List<ProductionRule> rules() {
        return Collections.unmodifiableList(productionRules);
    }

    /** Returns the named conditions in added order */
    public Iterator<NamedCondition> conditionIterator() { return namedConditions.values().iterator(); }

    /** Returns true if the given object is a rule base having the same name as this */
    public boolean equals(Object object) {
        if ( ! (object instanceof RuleBase)) return false;
        return ((RuleBase)object).getName().equals(this.getName());
    }

    public int hashCode() {
        return getName().hashCode();
    }

    public String toString() {
        return "rule base '" + getName() + "'";
    }

    /**
     * Returns a string containing all the rules and conditions of this rule base
     * in the form they will be evaluated, with all included rule bases inlined
     */
    public String toContentString() {
        StringBuilder b = new StringBuilder();
        for (Iterator<ProductionRule> i = productionRules.iterator(); i.hasNext(); ) {
            b.append(i.next());
            b.append("\n");
        }
        b.append("\n");
        b.append("\n");
        for (Iterator<NamedCondition> i = namedConditions.values().iterator(); i.hasNext(); ) {
            b.append(i.next());
            b.append("\n");
        }
        return b.toString();
    }

    /** A placeholder for an included rule base until it is inlined */
    private static class IncludeDirective extends ProductionRule {

        private final RuleBase includedBase;

        public IncludeDirective(RuleBase ruleBase) {
            this.includedBase = ruleBase;
        }

        public RuleBase getIncludedBase() { return includedBase; }

        /** Not used */
        public String getSymbol() { return ""; }

    }

}
