// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import com.yahoo.compress.IntegerCompressor;

/**
 * A set of words with differing exactness scores to be used for literal boost ranking.
 *
 * @author Steinar Knutsen
 */
public class WordAlternativesItem extends TermItem {

    private List<Alternative> alternatives;

    public WordAlternativesItem(String indexName, boolean isFromQuery, Substring origin, Collection<Alternative> terms) {
        super(indexName, isFromQuery, origin);
        setAlternatives(terms);
    }

    public void setAlternatives(Collection<Alternative> terms) {
        this.alternatives = uniqueAlternatives(terms);
    }

    private static ImmutableList<Alternative> uniqueAlternatives(Collection<Alternative> terms) {
        List<Alternative> uniqueTerms = new ArrayList<>(terms.size());
        for (Alternative term : terms) {
            int i = Collections.binarySearch(uniqueTerms, term, (t0, t1) -> t0.word.compareTo(t1.word));
            if (i >= 0) {
                Alternative old = uniqueTerms.get(i);
                if (old.exactness < term.exactness) {
                    uniqueTerms.set(i, term);
                }
            } else {
                uniqueTerms.add(~i, term);
            }
        }
        return ImmutableList.copyOf(uniqueTerms);
    }

    @Override
    public String stringValue() {
        StringBuilder builder = new StringBuilder();
        builder.append("[ ");
        for (Alternative a : alternatives) {
            builder.append(a.word).append("(").append(a.exactness).append(") ");
        }
        builder.append("]");
        return builder.toString();
    }

    @Override
    public boolean isStemmed() {
        return true;
    }

    @Override
    public int getNumWords() {
        return 1;
    }

    @Override
    public void setValue(String value) {
        throw new UnsupportedOperationException("Semantics for setting to a string would be brittle, use setAlternatives()");
    }

    @Override
    public String getRawWord() {
        if (getOrigin() == null) {
            return stringValue();
        } else {
            return getOrigin().getValue();
        }
    }

    @Override
    public boolean isWords() {
        return true;
    }

    @Override
    public String getIndexedString() {
        return alternatives.stream().map((x) -> x.word).collect(Collectors.joining(" "));
    }

    @Override
    public ItemType getItemType() {
        return ItemType.WORD_ALTERNATIVES; // placeholder
    }

    @Override
    public String getName() {
        return "WORD_ALTERNATIVES";
    }

    /**
     * Return an immutable snapshot of the contained terms. This list will not reflect later changes to the item.
     *
     * @return an immutable list of word alternatives and their respective scores
     */
    public List<Alternative> getAlternatives() {
        return alternatives;
    }

    @Override
    public void encodeThis(ByteBuffer target) {
        super.encodeThis(target);
        IntegerCompressor.putCompressedPositiveNumber(alternatives.size(), target);
        for (Alternative a : alternatives) {
            Item p = new PureWeightedString(a.word, (int) (getWeight() * a.exactness + 0.5));
            p.setFilter(isFilter());
            p.encode(target);
        }
    }

    /**
     * Add a new alternative iff the term string is not already present with an
     * equal or higher exactness score. If the term string is present with a
     * lower exactness score, the new, higher score will take precedence.
     *
     * @param term one of several string interpretations of the input word
     * @param exactness how close the term string matches what the user input
     */
    public void addTerm(String term, double exactness) {
        // do note, Item is Cloneable, and overwriting the reference is what
        // saves us from overriding the method
        if (alternatives.stream().anyMatch((a) -> a.word.equals(term) && a.exactness >= exactness )) return;

        List<Alternative> newTerms = new ArrayList<>(alternatives.size() + 1);
        newTerms.addAll(alternatives);
        newTerms.add(new Alternative(term, exactness));
        setAlternatives(newTerms);
    }

    @Override
    public WordAlternativesItem clone() {
        var clone = (WordAlternativesItem)super.clone();
        clone.alternatives = new ArrayList(this.alternatives);
        return clone;
    }

    @Override
    public boolean equals(Object other) {
        if ( ! super.equals(other)) return false;
        return this.alternatives.equals(((WordAlternativesItem)other).alternatives);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), alternatives);
    }

    /** A word alternative. This is a value object. */
    public static final class Alternative {

        public final String word;
        public final double exactness;

        public Alternative(String word, double exactness) {
            super();
            this.word = word;
            this.exactness = exactness;
        }

        @Override
        public String toString() {
            return "Alternative [word=" + word + ", exactness=" + exactness + "]";
        }

        @Override
        public boolean equals(Object o) {
            if ( ! (o instanceof Alternative)) return false;
            var other = (Alternative)o;
            if ( ! Objects.equals(this.word, other.word)) return false;
            if (this.exactness != other.exactness) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(word, exactness);
        }

    }

}
