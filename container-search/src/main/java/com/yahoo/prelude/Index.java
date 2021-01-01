// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude;

import com.yahoo.language.process.StemMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Information about configured settings of a field or field collection (an actual index or not) in a search definition.
 * There are two types of settings:
 * <ul>
 * <li><i>Typed commands</i> are checked using a particular is/get method
 * <li><i>Untyped commands</i> are checked using hasCommand and commandIterator
 * </ul>
 * addCommand sets both types.
 *
 * @author Steinar Knutsen
 * @author bratseth
 */
public class Index {

    public static class Attribute {

        private boolean tokenizedContent = false;
        public final String name;

        public Attribute(String name) {
            this.name = name;
        }

        public boolean isTokenizedContent() {
            return tokenizedContent;
        }

        public void setTokenizedContent(boolean tokenizedContent) {
            this.tokenizedContent = tokenizedContent;
        }
    }

    /** The null index - don't use this for name lookups */
    public static final Index nullIndex = new Index("(null)");

    private final String name;

    private String type; // TODO: Parse to a type object; do not expose this as a string

    private final List<String> aliases = new ArrayList<>();

    // The state resulting from adding commands to this (using addCommand)
    private boolean tensor = false;
    private boolean uriIndex = false;
    private boolean hostIndex = false;
    private StemMode stemMode = StemMode.NONE;
    private boolean isAttribute = false;
    private boolean isDefaultPosition = false;
    private boolean dynamicSummary=false;
    private boolean highlightSummary=false;
    private boolean lowercase = false;
    private boolean plainTokens = false;
    private boolean multivalue = false;
    private boolean fastSearch = false;
    private boolean normalize = false;
    private boolean literalBoost = false;
    private boolean numerical = false;
    private boolean predicate = false;
    private long predicateUpperBound = Long.MAX_VALUE;
    private long predicateLowerBound = Long.MIN_VALUE;

    /** True if this is an <i>exact</i> index - which should match  tokens containing any characters */
    private boolean exact = false;

    private boolean isNGram = false;
    private int gramSize = 2;

    /** Whether implicit phrases should lead to a phrase item or an and item. */
    private Boolean phraseSegmenting = false;

    /** The string terminating an exact token in this index, or null to use the default (space) */
    private String exactTerminator = null;

    /** Commands which are not converted into a field */
    private final Set<String> commands = new java.util.HashSet<>();

    /** All the commands added to this, including those converted to fields above */
    private final List<String> allCommands = new java.util.ArrayList<>();

    public Index(String name) {
        this.name = name;
    }

    public void addAlias(String alias) { aliases.add(alias); }

    /** Returns an unmodifiable list of the aliases of this index (not including the index proper name) */
    public List<String> aliases() { return Collections.unmodifiableList(aliases); }

    /**
     * Returns the canonical name of this index, unless it
     * is the null index, which doesn't have a canonical name
     */
    public String getName() {
        return name;
    }

    public boolean isUriIndex() {
        return uriIndex;
    }

    public boolean isDefaultPosition() {
        return isDefaultPosition;
    }

    public void setDefaultPosition(boolean v) {
        isDefaultPosition = v;
    }

    public void setUriIndex(boolean uriIndex) {
        this.uriIndex = uriIndex;
    }

    public boolean isHostIndex() {
        return hostIndex;
    }

    public void setHostIndex(boolean hostIndex) {
        this.hostIndex = hostIndex;
    }

    public StemMode getStemMode() {
        return stemMode;
    }

    public void setStemMode(StemMode stemMode) {
        this.stemMode = stemMode;
    }

    public void setStemMode(String name) {
        this.stemMode = StemMode.valueOf(name);
    }

    /** Adds a type or untyped command string to this */
    public Index addCommand(String command) {
        allCommands.add(command);

        if (command.startsWith("type tensor(") || command.startsWith("type tensor<")) { // TODO: Type info can replace numerical, predicate, multivalue
            setTensor(true);
        } else if ("fullurl".equals(command)) {
            setUriIndex(true);
        } else if ("urlhost".equals(command)) {
            setHostIndex(true);
        } else if (command.startsWith("stem ")) {
            setStemMode(command.substring(5));
        } else if (command.startsWith("stem:")) {
            setStemMode(command.substring(5));
        } else if ("stem".equals(command)) {
            setStemMode(StemMode.SHORTEST);
        } else if ("word".equals(command)) {
            setExact(true, null);
        } else if ("exact".equals(command)) {
            setExact(true, " ");
        } else if ("dynteaser".equals(command)) {
            setDynamicSummary(true);
        } else if ("highlight".equals(command)) {
            setHighlightSummary(true);
        } else if ("lowercase".equals(command)) {
            setLowercase(true);
        } else if (command.startsWith("exact ")) {
            setExact(true, command.substring(6));
        } else if (command.startsWith("ngram ")) {
            setNGram(true, Integer.parseInt(command.substring(6)));
        } else if (command.equals("attribute")) {
            setAttribute(true);
        } else if (command.equals("default-position")) {
            setDefaultPosition(true);
        } else if (command.equals("plain-tokens")) {
            setPlainTokens(true);
        } else if (command.equals("multivalue")) {
            setMultivalue(true);
        } else if (command.equals("fast-search")) {
            setFastSearch(true);
        } else if (command.equals("normalize")) {
            setNormalize(true);
        } else if (command.equals("literal-boost")) {
            setLiteralBoost(true);
        } else if (command.equals("numerical")) {
            setNumerical(true);
        } else if (command.equals("predicate")) {
            setPredicate(true);
        } else if (command.startsWith("predicate-bounds ")) {
            setPredicateBounds(command.substring(17));
        } else if (command.equals("phrase-segmenting")) {
            setPhraseSegmenting(true);
        } else if (command.startsWith("phrase-segmenting ")) {
            setPhraseSegmenting(Boolean.parseBoolean(command.substring("phrase-segmenting ".length())));
        } else {
            commands.add(command);
        }
        return this;
    }

    private void setTensor(boolean tensor) {
        this.tensor = tensor;
    }

    public boolean isTensor() { return tensor; }

    private void setPredicateBounds(String bounds) {
        if ( ! bounds.startsWith("[..")) {
            predicateLowerBound = Long.parseLong(bounds.substring(1, bounds.indexOf("..")));
        } else {
            predicateLowerBound = Long.MIN_VALUE;
        }
        if ( ! bounds.endsWith("..]")) {
            predicateUpperBound = Long.parseLong(bounds.substring(bounds.indexOf("..") + 2, bounds.length() - 1));
        } else {
            predicateUpperBound = Long.MAX_VALUE;
        }
    }

    /** Sets whether terms in this field are lowercased when indexing. */
    public void setLowercase(boolean lowercase) {
        this.lowercase = lowercase;
    }

    /** Returns whether terms in this field are lowercased when indexing. */
    public boolean isLowercase() {
        return lowercase;
    }

    /** Returns an iterator of all the untyped commands of this */
    public Iterator<String> commandIterator() {
        return commands.iterator();
    }

    /** Checks whether this has the given (exact) <i>untyped</i> command string */
    public boolean hasCommand(String commandString) {
        return commands.contains(commandString);
    }

    /**
     * Set whether this index should match any kind of characters
     *
     * @param exact true to make this index match any kind of characters, not just word and digit ones
     * @param terminator the terminator of an exact sequence (one or more characters),
     *        or null to use the default (space)
     */
    public void setExact(boolean exact, String terminator) {
        this.exact = exact;
        this.exactTerminator = terminator;
    }

    /** Returns whether this is an exact index, which should match tokens containing any characters */
    public boolean isExact() { return exact; }

    /** Returns the string terminating an exact sequence in this index, or null to use the default (space) */
    public String getExactTerminator() { return exactTerminator; }

    /** Returns true if this is an ngram index (default: false) */
    public boolean isNGram() { return isNGram; }

    /** Returns the gram size. Only used if isNGram is true (default: 2)*/
    public int getGramSize() { return gramSize; }

    public void setNGram(boolean nGram,int gramSize) {
        this.isNGram = nGram;
        this.gramSize = gramSize;
    }

    public void setDynamicSummary(boolean dynamicSummary) { this.dynamicSummary=dynamicSummary; }
    public boolean getDynamicSummary() { return dynamicSummary; }

    public void setHighlightSummary(boolean highlightSummary) { this.highlightSummary=highlightSummary; }
    public boolean getHighlightSummary() { return highlightSummary; }

    /** Returns true if this is the null index */
    // TODO: Replace by == Index.null
    public boolean isNull() {
        return "(null)".equals(name);
    }

    public boolean isAttribute() { return isAttribute; }

    public void setAttribute(boolean isAttribute) {
        this.isAttribute = isAttribute;
    }

    public boolean hasPlainTokens() { return plainTokens; }

    public void setPlainTokens(boolean plainTokens) {
        this.plainTokens = plainTokens;
    }

    public void setMultivalue(boolean multivalue) { this.multivalue = multivalue; }

    /** Returns true if this is a multivalue field */
    public boolean isMultivalue() { return multivalue; }

    public void setFastSearch(boolean fastSearch) { this.fastSearch = fastSearch; }

    /** Returns true if this is an attribute with fastsearch turned on */
    public boolean isFastSearch() { return fastSearch; }

    public void setNormalize(boolean normalize) { this.normalize = normalize; }

    /** Returns true if the content of this index is normalized */
    public boolean getNormalize() { return normalize; }

    public boolean getLiteralBoost() { return literalBoost; }

    public void setLiteralBoost(boolean literalBoost) { this.literalBoost = literalBoost; }

    public void setNumerical(boolean numerical) { this.numerical = numerical; }

    public boolean isNumerical() { return numerical; }

    public void setPredicate(boolean isPredicate) { this.predicate = isPredicate; }

    public boolean isPredicate() { return predicate; }

    public long getPredicateUpperBound() { return predicateUpperBound; }

    public long getPredicateLowerBound() { return predicateLowerBound; }

    public boolean getPhraseSegmenting() { return phraseSegmenting; }

    public boolean setPhraseSegmenting(boolean phraseSegmenting) { return this.phraseSegmenting = phraseSegmenting; }

    /** Returns all the literal command strings given as arguments to addCommand in this instance */
    public List<String> allCommands() { return allCommands; }

    @Override
    public String toString() {
        return "index '" + getName() + "'";
    }

}
