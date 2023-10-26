// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.ranking.features.fieldmatch;

import java.util.Arrays;
import java.util.List;

/**
 * Represents a document field which can be matched and ranked against.
 *
 * @author bratseth
 */
public class Field {

    private final List<Term> terms;

    /** Creates a field from a space-separated string */
    public Field(String fieldString) {
        terms = Arrays.stream(fieldString.split(" ")).map(Term::new).toList();

    }

    /** Creates a field from a list of terms */
    public Field(List<Term> terms) {
        this.terms = List.copyOf(terms);
    }

    /** Returns an immutable list of the terms in this */
    public List<Term> terms() { return terms; }

    /** A term in a field */
    public static class Term {

        private final String value;
        private final float exactness;

        /** Creates a term with the given value and full exactness (1.0) */
        public Term(String value) {
            this(value, 1.0f);
        }

        public Term(String value, float exactness) {
            this.value = value;
            this.exactness = exactness;
        }

        /** Returns the string value of this term */
        public String value() { return value; }

        /**
         * Returns the degree to which this term is exactly what was in the document (1.0),
         * or some stemmed form (closer to 0)
         */
        public float exactness() { return exactness; }

    }

}
