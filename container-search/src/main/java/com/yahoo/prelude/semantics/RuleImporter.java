// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;

import com.yahoo.io.IOUtils;
import com.yahoo.language.Linguistics;
import com.yahoo.prelude.semantics.parser.ParseException;
import com.yahoo.prelude.semantics.parser.SemanticsParser;

/**
 * Imports rule bases from various sources.
 *
 * @author bratseth
 */
// Uses the JavaCC-generated parser to read rule bases.
// This is an intermediate between the parser and the rule base being loaded
// on implementation of some directives, for example, it knows where to find
// rule bases included into others, while neither the rule base or the parser knows.
public class RuleImporter {

    /** If this is set, imported rule bases are looked up in this config otherwise, they are looked up as files. */
    private final SemanticRulesConfig config;

    /** Ignore requests to read automata files. Useful to validate rule bases without having automatas present. */
    private final boolean ignoreAutomatas;

    /** Ignore requests to include files. Useful to validate rule bases one by one in config. */
    private final boolean ignoreIncludes;

    private Linguistics linguistics;

    /** Create a rule importer which will read from file */
    public RuleImporter(Linguistics linguistics) {
        this(null, false, linguistics);
    }

    /** Create a rule importer which will read from a config object */
    public RuleImporter(SemanticRulesConfig config, Linguistics linguistics) {
        this(config, false, linguistics);
    }

    public RuleImporter(boolean ignoreAutomatas, Linguistics linguistics) {
        this(null, ignoreAutomatas, linguistics);
    }

    public RuleImporter(boolean ignoreAutomatas, boolean ignoreIncludes, Linguistics linguistics) {
        this(null, ignoreAutomatas, ignoreIncludes, linguistics);
    }

    public RuleImporter(SemanticRulesConfig config, boolean ignoreAutomatas, Linguistics linguistics) {
        this(config, ignoreAutomatas, false, linguistics);
    }

    public RuleImporter(SemanticRulesConfig config,
                        boolean ignoreAutomatas,
                        boolean ignoreIncludes,
                        Linguistics linguistics) {
        this.config = config;
        this.ignoreAutomatas = ignoreAutomatas;
        this.ignoreIncludes = ignoreIncludes;
        this.linguistics = linguistics;
    }

    /**
     * Imports semantic rules from a file
     *
     * @param fileName the rule file to use
     * @throws java.io.IOException if the file can not be read for some reason
     * @throws ParseException if the file does not contain a valid semantic rule set
     */
    public RuleBase importFile(String fileName) throws IOException, ParseException {
        return importFile(fileName, null);
    }

    /**
     * Imports semantic rules from a file
     *
     * @param fileName the rule file to use
     * @param automataFile the automata file to use, or null to not use any
     * @throws java.io.IOException if the file can not be read for some reason
     * @throws ParseException if the file does not contain a valid semantic rule set
     */
    public RuleBase importFile(String fileName, String automataFile) throws IOException, ParseException {
        var ruleBase = privateImportFile(fileName, automataFile);
        ruleBase.initialize();
        return ruleBase;
    }

    public RuleBase privateImportFile(String fileName, String automataFile) throws IOException, ParseException {
        BufferedReader reader = null;
        try {
            reader = IOUtils.createReader(fileName, "utf-8");
            File file = new File(fileName);
            String absoluteFileName = file.getAbsolutePath();
            var ruleBase = new RuleBase(stripLastName(file.getName()));
            privateImportFromReader(reader, absoluteFileName, automataFile, ruleBase);
            return ruleBase;
        }
        finally {
            IOUtils.closeReader(reader);
        }
    }

    /** Imports all the rule files (files ending by "sr") in the given directory */
    public List<RuleBase> importDir(String ruleBaseDir) throws IOException, ParseException {
        File ruleBaseDirFile = new File(ruleBaseDir);
        if ( ! ruleBaseDirFile.exists())
            throw new IOException("Rule base dir '" + ruleBaseDirFile.getAbsolutePath() + "' does not exist");
        File[] files = ruleBaseDirFile.listFiles();
        Arrays.sort(files);
        List<RuleBase> ruleBases = new java.util.ArrayList<>();
        for (File file : files) {
            if ( ! file.getName().endsWith(".sr")) continue;
            RuleBase base = importFile(file.getAbsolutePath());
            ruleBases.add(base);
        }
        return ruleBases;
    }

    /** Read and include a rule base in another */
    public void include(String ruleBaseName, RuleBase ruleBase) throws java.io.IOException, ParseException {
        if (ignoreIncludes) return;
        RuleBase include;
        if (config == null) {
            include = privateImportFromDirectory(ruleBaseName, ruleBase);
        }
        else {
            include = privateImportFromConfig(ruleBaseName);
        }
        ruleBase.include(include);
    }

    /** Returns an unitialized rule base */
    private RuleBase privateImportFromDirectory(String ruleBaseName, RuleBase ruleBase) throws IOException, ParseException {
        String includeDir = new File(ruleBase.getSource()).getParentFile().getAbsolutePath();
        if (!ruleBaseName.endsWith(".sr"))
            ruleBaseName = ruleBaseName + ".sr";
        File importFile = new File(includeDir, ruleBaseName);
        if ( ! importFile.exists())
            throw new IOException("No file named '" + shortenPath(importFile.getPath()) + "'");
        return privateImportFile(importFile.getPath(), null);
    }

    /** Returns an unitialized rule base */
    private RuleBase privateImportFromConfig(String ruleBaseName) throws ParseException {
        SemanticRulesConfig.Rulebase ruleBaseConfig = findRuleBaseConfig(config,ruleBaseName);
        if (ruleBaseConfig == null)
            ruleBaseConfig = findRuleBaseConfig(config, stripLastName(ruleBaseName));
        if (ruleBaseConfig == null)
             throw new ParseException("Could not find included rule base '" + ruleBaseName + "'");
        return privateImportConfig(ruleBaseConfig);
    }

    private SemanticRulesConfig.Rulebase findRuleBaseConfig(SemanticRulesConfig config, String ruleBaseName) {
        for (Object aRulebase : config.rulebase()) {
            SemanticRulesConfig.Rulebase ruleBaseConfig = (SemanticRulesConfig.Rulebase)aRulebase;
            if (ruleBaseConfig.name().equals(ruleBaseName))
                return ruleBaseConfig;
        }
        return null;
    }

    public void setAutomata(RuleBase base, String automata) {
        if (ignoreAutomatas)
            base.setUsesAutomata(true); // Stop it from failing on automata condition references
        else
            base.setAutomataFile(automata);
    }

    static String stripLastName(String fileName) {
        int lastDotIndex = fileName.lastIndexOf(".");
        if (lastDotIndex < 0) return fileName;
        return fileName.substring(0, lastDotIndex);
    }

    public RuleBase importString(String string, String automataFile) throws IOException, ParseException {
        return importString(string, automataFile, null, null);
    }

    public RuleBase importString(String string, String automataFile, String sourceName) throws IOException, ParseException {
        return importString(string, automataFile, sourceName, null);
    }

    public RuleBase importString(String string, String automataFile, RuleBase ruleBase) throws IOException, ParseException {
        return importString(string, automataFile, null, ruleBase);
    }

    public RuleBase importString(String string, String automataFile, String sourceName, RuleBase ruleBase) throws IOException, ParseException {
        return importFromReader(new StringReader(string), sourceName, automataFile, ruleBase);
    }

    public RuleBase importConfig(SemanticRulesConfig.Rulebase ruleBaseConfig) throws IOException, ParseException {
        RuleBase ruleBase = privateImportConfig(ruleBaseConfig);
        ruleBase.initialize();
        return ruleBase;
    }

    /** Imports an unitialized rule base */
    public RuleBase privateImportConfig(SemanticRulesConfig.Rulebase ruleBaseConfig) throws ParseException {
        if (config == null) throw new IllegalStateException("Must initialize with config if importing from config");
        RuleBase ruleBase = new RuleBase(ruleBaseConfig.name());
        return privateImportFromReader(new StringReader(ruleBaseConfig.rules()),
                                       "semantic-rules.cfg",
                                       ruleBaseConfig.automata(),ruleBase);
    }

    public RuleBase importFromReader(Reader reader, String sourceInfo, String automataFile) throws ParseException {
        return importFromReader(reader, sourceInfo, automataFile, null);
    }

    /**
     * Imports rules from a reader
     *
     * @param reader the reader containing rules on the proper syntax
     * @param sourceName a string describing the source of the rules used for error messages
     * @param ruleBase an existing rule base to import the rules into, or null to create a new one
     * @return the rule base containing the rules added from the reader
     * @throws ParseException if the reader contains illegal rule syntax
     */
    public RuleBase importFromReader(Reader reader, String sourceName, String automataFile, RuleBase ruleBase) throws ParseException {
        ruleBase = privateImportFromReader(reader, sourceName, automataFile, ruleBase);
        ruleBase.initialize();
        return ruleBase;
    }

    /** Returns an unitialized rule base */
    public RuleBase privateImportFromReader(Reader reader, String sourceName, String automataFile, RuleBase ruleBase) throws ParseException {
        try {
            if (ruleBase == null)
                ruleBase = new RuleBase(sourceName == null ? "anonymous" : sourceName);
            ruleBase.setSource(sourceName.replace('\\', '/'));
            new SemanticsParser(reader, linguistics).semanticRules(ruleBase, this);
            if (automataFile != null && !automataFile.isEmpty())
                ruleBase.setAutomataFile(automataFile.replace('\\', '/'));
            return ruleBase;
        } catch (Throwable t) { // also catches token mgr errors
            ParseException p = new ParseException("Could not parse '" + shortenPath(sourceName) + "'");
            p.initCause(t);
            throw p;
        }
    }

    /**
     * Snips what's in from of rules/ if "rules/" is present in the string
     * to avoid displaying details about where application content is copied
     * (if rules/ is present, these rules are read from an applicatino package)
     */
    private static String shortenPath(String path) {
        int rulesIndex = path.indexOf("rules/");
        if (rulesIndex < 0) return path;
        return path.substring(rulesIndex);
    }

}
