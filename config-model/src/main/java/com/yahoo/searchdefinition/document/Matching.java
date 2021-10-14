// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.document;

import java.io.Serializable;

/**
 * Defines how a field should be matched.
 * Matching objects can be compared based on their content, but they are <i>not</i> immutable.
 *
 * @author bratseth
 */
public class Matching implements Cloneable, Serializable {

    public static final Type defaultType = Type.TEXT;

    public enum Type {
        TEXT("text"),
        WORD("word"),
        EXACT("exact"),
        GRAM("gram");
        private String name;
        Type(String name) { this.name = name; }
        public String getName() { return name; }
    }

    /** Which match algorithm is used by this matching setup */
    public enum Algorithm {
        NORMAL("normal"),
        PREFIX("prefix"),
        SUBSTRING("substring"),
        SUFFIX("suffix");
        private String name;
        Algorithm(String name) { this.name = name; }
        public String getName() { return name; }
    }

    private Type type = Type.TEXT;
    private Case casing = Case.UNCASED;

    /** The basic match algorithm */
    private Algorithm algorithm = Algorithm.NORMAL;

    private boolean typeUserSet = false;

    private boolean algorithmUserSet = false;

    /** The gram size is the n in n-gram, or -1 if not set. Should only be set with gram matching. */
    private int gramSize=-1;

    /** Maximum number of characters to consider when searching in this field. Used for limiting resources, especially in streaming search. */
    private Integer maxLength;

    private String exactMatchTerminator=null;

    /** Creates a matching of type "text" */
    public Matching() {}

    public Matching(Type type) {
        this.type = type;
    }

    public Type getType() { return type; }
    public Case getCase() { return casing; }

    public void setType(Type type) {
        this.type = type;
        typeUserSet = true;
    }

    public void setCase(Case casing) { this.casing = casing; }

    public Integer maxLength() { return maxLength; }
    public Matching maxLength(int maxLength) { this.maxLength = maxLength; return this; }
    public boolean isTypeUserSet() { return typeUserSet; }

    public Algorithm getAlgorithm() { return algorithm; }

    public void setAlgorithm(Algorithm algorithm) {
        this.algorithm = algorithm;
        algorithmUserSet = true;
    }

    public boolean isAlgorithmUserSet() { return algorithmUserSet; }

    public boolean isPrefix() { return algorithm == Algorithm.PREFIX; }

    public boolean isSubstring() { return algorithm == Algorithm.SUBSTRING; }

    public boolean isSuffix() { return algorithm == Algorithm.SUFFIX; }

    /** Returns the gram size, or -1 if not set. Should only be set with gram matching. */
    public int getGramSize() { return gramSize; }

    public void setGramSize(int gramSize) { this.gramSize=gramSize; }

    /**
     * Merge data from another matching object
     */
    public void merge(Matching m) {
        if (m.isAlgorithmUserSet()) {
            this.setAlgorithm(m.getAlgorithm());
        }
        if (m.isTypeUserSet()) {
            this.setType(m.getType());
            if (m.getType()==Type.GRAM)
              gramSize=m.gramSize;
        }
        if (m.getExactMatchTerminator() != null) {
            this.setExactMatchTerminator(m.getExactMatchTerminator());
        }
    }

    /**
     * If exact matching is used, this returns the terminator string
     * which terminates an exact matched sequence in queries. If exact
     * matching is not used, or no terminator is set, this is null
     */
    public String getExactMatchTerminator() { return exactMatchTerminator; }

    /**
     * Sets the terminator string which terminates an exact matched
     * sequence in queries (used if type is EXACT).
     */
    public void setExactMatchTerminator(String exactMatchTerminator) {
        this.exactMatchTerminator = exactMatchTerminator;
    }

    public String toString() {
        return type + " matching [" + (type==Type.GRAM ? "gram size " + gramSize : "supports " + algorithm) + "], [exact-terminator "+exactMatchTerminator+"]";
    }

    public Matching clone() {
        try {
            return (Matching)super.clone();
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException("Programming error");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (! (o instanceof Matching)) return false;

        Matching other=(Matching)o;
        if ( ! other.type.equals(this.type)) return false;
        if ( ! other.algorithm.equals(this.algorithm)) return false;
        if ( this.exactMatchTerminator==null && other.exactMatchTerminator!=null) return false;
        if ( this.exactMatchTerminator!=null && ( ! this.exactMatchTerminator.equals(other.exactMatchTerminator)) )
            return false;
        if ( gramSize!=other.gramSize) return false;
        return true;
    }

    @Override public int hashCode() {
        return java.util.Objects.hash(type, algorithm, exactMatchTerminator, gramSize);
    }

}
