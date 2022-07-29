// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import com.yahoo.prelude.query.WordAlternativesItem.Alternative;

/**
 * Functional test for the contracts in WordAlternativesItem.
 *
 * @author steinar
 */
public class WordAlternativesItemTestCase {

    @Test
    final void testWordAlternativesItem() {
        List<Alternative> terms = new ArrayList<>();
        List<Alternative> expected;
        terms.add(new Alternative("1", 1.0));
        terms.add(new Alternative("2", 1.0));
        terms.add(new Alternative("3", 1.0));
        terms.add(new Alternative("4", 1.0));
        expected = new ArrayList<>(terms);
        terms.add(new Alternative("1", .1));
        terms.add(new Alternative("2", .2));
        terms.add(new Alternative("3", .3));
        terms.add(new Alternative("4", .4));
        WordAlternativesItem w = new WordAlternativesItem("", true, null, terms);
        assertEquals(expected, w.getAlternatives());
    }

    @Test
    final void testSetAlternatives() {
        List<Alternative> terms = new ArrayList<>();
        terms.add(new Alternative("1", 1.0));
        terms.add(new Alternative("2", 1.0));
        WordAlternativesItem w = new WordAlternativesItem("", true, null, terms);
        terms.add(new Alternative("1", 1.5));
        terms.add(new Alternative("2", 0.5));
        w.setAlternatives(terms);
        assertTrue(w.getAlternatives().stream().anyMatch((a) -> a.word.equals("1") && a.exactness == 1.5),
                "Could not overwrite alternative");
        assertTrue(w.getAlternatives().stream().anyMatch((a) -> a.word.equals("2") && a.exactness == 1.0),
                "Old alternative unexpectedly removed");
        assertEquals(2, w.getAlternatives().size());
        terms.add(new Alternative("3", 0.5));
        w.setAlternatives(terms);
        assertTrue(w.getAlternatives().stream().anyMatch((a) -> a.word.equals("3") && a.exactness == 0.5),
                "Could not add new term");
    }

    @Test
    final void testAddTerm() {
        List<Alternative> terms = new ArrayList<>();
        terms.add(new Alternative("1", 1.0));
        terms.add(new Alternative("2", 1.0));
        WordAlternativesItem w = new WordAlternativesItem("", true, null, terms);
        w.addTerm("1", 0.1);
        assertEquals(terms, w.getAlternatives());
        w.addTerm("1", 2.0);
        assertTrue(w.getAlternatives().stream().anyMatch((a) -> a.word.equals("1") && a.exactness == 2.0),
                "Could not add new alternative");
        assertEquals(2, w.getAlternatives().size());
        w.addTerm("3", 0.5);
        assertTrue(w.getAlternatives().stream().anyMatch((a) -> a.word.equals("3") && a.exactness == 0.5),
                "Could not add new term");
    }

}
