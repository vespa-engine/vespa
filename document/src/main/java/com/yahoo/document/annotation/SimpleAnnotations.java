// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import com.yahoo.document.datatypes.StringFieldValue;

/**
 * Lightweight representation of TERM annotations for indexing.
 * Uses flat arrays instead of object graphs for 80-90% memory reduction.
 * NOT part of public API - internal optimization for indexing performance.
 *
 * This class can only represent simple TERM annotations with:
 * - Position (from, length)
 * - Optional term override (when term differs from substring)
 *
 * Memory footprint: ~8 bytes + arrays vs ~300 bytes per annotation with full SpanTree.
 *
 * @author havardpe
 */
public final class SimpleAnnotations {

    // Flat arrays for maximum memory density
    private int[] positions;      // [from1, len1, from2, len2, ...]
    private String[] terms;       // [term1, term2, ...] - null when term equals substring
    private int count;

    public SimpleAnnotations() {
        this.positions = new int[32];   // Start with capacity for 16 annotations
        this.terms = new String[16];
        this.count = 0;
    }

    /**
     * Add a TERM annotation.
     *
     * @param from the start position in the text (character offset)
     * @param length the length of the span (in characters)
     * @param term the term to index, or null if term equals the substring of original text
     */
    public void add(int from, int length, String term) {
        ensureCapacity();
        positions[count * 2] = from;
        positions[count * 2 + 1] = length;
        terms[count] = term;
        count++;
    }

    private void ensureCapacity() {
        if (count * 2 >= positions.length) {
            // Grow by 2x
            int[] newPos = new int[positions.length * 2];
            String[] newTerms = new String[terms.length * 2];
            System.arraycopy(positions, 0, newPos, 0, count * 2);
            System.arraycopy(terms, 0, newTerms, 0, count);
            positions = newPos;
            terms = newTerms;
        }
    }

    public int getCount() {
        return count;
    }

    public int getFrom(int idx) {
        return positions[idx * 2];
    }

    public int getLength(int idx) {
        return positions[idx * 2 + 1];
    }

    /**
     * Get the term override for annotation at index, or null if term equals substring.
     */
    public String getTerm(int idx) {
        return terms[idx];
    }

    /**
     * Convert to full SpanTree representation for API compatibility.
     * This is only called when code actually needs to iterate over annotations,
     * which is rare (mainly deprecated FlattenExpression and tests).
     * Serialization uses direct path and never calls this.
     */
    public SpanTree toSpanTree(String name) {
        SpanTree tree = new SpanTree(name);
        for (int i = 0; i < count; i++) {
            int from = getFrom(i);
            int length = getLength(i);
            Span span = tree.spanList().span(from, length);
            String term = getTerm(i);

            if (term != null) {
                tree.annotate(span, new Annotation(AnnotationTypes.TERM,
                                                   new StringFieldValue(term)));
            } else {
                tree.annotate(span, new Annotation(AnnotationTypes.TERM));
            }
        }
        return tree;
    }

    @Override
    public String toString() {
        return "SimpleAnnotations with " + count + " TERM annotations";
    }
}
