// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude;


import com.yahoo.language.process.StemMode;

import java.util.Iterator;
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

    private String name;
    private boolean uriIndex = false;
    private boolean hostIndex = false;
    private StemMode stemMode = StemMode.NONE;
    private Attribute[] matchGroup = null;
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
    private long predicateUpperBound = Long.MAX_VALUE;
    private long predicateLowerBound = Long.MIN_VALUE;

    /**
     * True if this is an <i>exact</i> index - which should match
     * tokens containing any characters
     */
    private boolean exact = false;

    private boolean isNGram = false;
    private int gramSize = 2;

    /**
     * The string terminating an exact token in this index,
     * or null to use the default (space)
     */
    private String exactTerminator = null;

    private Set<String> commands = new java.util.HashSet<>();

    public Index(String name) {
        this.name = name;
    }

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
    public Index addCommand(String commandString) {
        if ("fullurl".equals(commandString)) {
            setUriIndex(true);
        } else if ("urlhost".equals(commandString)) {
            setHostIndex(true);
        } else if (commandString.startsWith("stem ")) {
            setStemMode(commandString.substring(5));
        } else if (commandString.startsWith("stem:")) {
            setStemMode(commandString.substring(5));
        } else if ("stem".equals(commandString)) {
            setStemMode(StemMode.SHORTEST);
        } else if ("word".equals(commandString)) {
            setExact(true, null);
        } else if ("exact".equals(commandString)) {
            setExact(true, " ");
        } else if ("dynteaser".equals(commandString)) {
            setDynamicSummary(true);
        } else if ("highlight".equals(commandString)) {
            setHighlightSummary(true);
        } else if ("lowercase".equals(commandString)) {
            setLowercase(true);
        } else if (commandString.startsWith("exact ")) {
            setExact(true, commandString.substring(6));
        } else if (commandString.startsWith("ngram ")) {
            setNGram(true,Integer.parseInt(commandString.substring(6)));
        } else if (commandString.equals("attribute")) {
            setAttribute(true);
        } else if (commandString.equals("default-position")) {
            setDefaultPosition(true);
        } else if (commandString.startsWith("match-group ")) {
            setMatchGroup(commandString.substring(12).split(" "));
        } else if (commandString.equals("plain-tokens")) {
            setPlainTokens(true);
        } else if (commandString.equals("multivalue")) {
            setMultivalue(true);
        } else if (commandString.equals("fast-search")) {
            setFastSearch(true);
        } else if (commandString.equals("normalize")) {
            setNormalize(true);
        } else if (commandString.equals("literal-boost")) {
            setLiteralBoost(true);
        } else if (commandString.equals("numerical")) {
            setNumerical(true);
        } else if (commandString.startsWith("predicate-bounds ")) {
            setPredicateBounds(commandString.substring(17));
        } else {
            commands.add(commandString);
        }
        return this;
    }

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

    /**
     * Whether terms in this field are lower cased when indexing.
     *
     * @param lowercase true if terms are lowercased
     */
    public void setLowercase(boolean lowercase) {
        this.lowercase = lowercase;
    }

    /**
     * Whether terms in this field are lower cased when indexing.
     *
     * @return true if terms are lowercased
     */
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
        this.isNGram=nGram;
        this.gramSize=gramSize;
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

    public Attribute[] getMatchGroup() { // TODO: Not in use on Vespa 6
        return matchGroup;
    }

    public void setMatchGroup(String[] attributes) {
        Attribute[] a = new Attribute[attributes.length];

        for (int i = 0; i < attributes.length; i++) {
            a[i] = new Attribute(attributes[i].trim());
        }
        this.matchGroup = a;
    }

    public boolean isAttribute() {
        return isAttribute;
    }

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

    public long getPredicateUpperBound() { return predicateUpperBound; }

    public long getPredicateLowerBound() { return predicateLowerBound; }

    @Override
    public String toString() {
        return "index '" + getName() + "'";
    }

}
