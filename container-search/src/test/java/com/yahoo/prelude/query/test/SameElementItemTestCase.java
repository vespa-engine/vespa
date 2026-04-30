// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.test;

import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.AndSegmentItem;
import com.yahoo.prelude.query.PhraseItem;
import com.yahoo.prelude.query.PhraseSegmentItem;
import com.yahoo.prelude.query.SameElementItem;
import com.yahoo.prelude.query.WordItem;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class SameElementItemTestCase {

    @Test
    void testAddItem() {
        SameElementItem s = new SameElementItem("structa");
        s.addItem(new WordItem("b", "f1"));
        s.addItem(new WordItem("c", "f2"));
        s.addItem(new WordItem("d", "f3"));
        assertEquals("structa:{f1:b f2:c f3:d}", s.toString());
    }

    @Test
    void testClone() {
        SameElementItem s = new SameElementItem("structa");
        s.addItem(new WordItem("b", "f1"));
        s.addItem(new WordItem("c", "f2"));
        s.addItem(new WordItem("d", "f3"));
        assertEquals("structa:{f1:b f2:c f3:d}", s.toString());
        SameElementItem c = (SameElementItem) s.clone();
        assertEquals("structa:{f1:b f2:c f3:d}", c.toString());
    }

    @Test
    void requireAllowCommonPrefix() {
        SameElementItem s = new SameElementItem("structa");
        s.addItem(new WordItem("b", "f1"));
        s.addItem(new WordItem("c", "structaf2"));
        assertEquals("structa:{f1:b structaf2:c}", s.toString());
    }

    @Test
    void requireChildrenCanHavePrefixCommonWithParent() {
        SameElementItem s = new SameElementItem("structa");
        s.addItem(new WordItem("b", "f1"));
        s.addItem(new WordItem("c", "structa.f2"));
        assertEquals("structa:{f1:b structa.f2:c}", s.toString());
    }

    @Test
    void testGetFieldName() {
        SameElementItem parent = new SameElementItem("myArray");
        WordItem word = new WordItem("value", "x");
        parent.addItem(word);
        assertEquals("x", word.getIndexName());
        assertEquals("myArray.x", word.getFieldName());
    }

    @Test
    void testGetFieldNameOutsideSameElement() {
        WordItem word = new WordItem("value", "x");
        assertEquals("x", word.getIndexName());
        assertEquals("x", word.getFieldName(), "Outside SameElement, getFieldName returns getIndexName");

        AndItem and = new AndItem();
        and.addItem(word);
        assertEquals("x", word.getFieldName(), "Inside non-SameElement parent, getFieldName returns getIndexName");
    }

    @Test
    void testGetFieldNameForCompositeIndexedItem() {
        SameElementItem parent = new SameElementItem("myArray");
        PhraseItem phrase = new PhraseItem();
        phrase.setIndexName("x");
        phrase.addItem(new WordItem("a"));
        parent.addItem(phrase);
        assertEquals("x", phrase.getIndexName());
        assertEquals("myArray.x", phrase.getFieldName());
    }

    @Test
    void testGetFieldNameForIndexedSegmentItem() {
        SameElementItem parent = new SameElementItem("myArray");
        PhraseSegmentItem segment = new PhraseSegmentItem("a b", true, false);
        segment.setIndexName("x");
        segment.addItem(new WordItem("a"));
        segment.addItem(new WordItem("b"));
        segment.lock();
        parent.addItem(segment);
        assertEquals("x", segment.getIndexName());
        assertEquals("myArray.x", segment.getFieldName());
    }

    @Test
    void testGetFieldNameForAndSegmentItem() {
        SameElementItem parent = new SameElementItem("myArray");
        AndSegmentItem segment = new AndSegmentItem("a b", true, false);
        segment.addItem(new WordItem("a", "x", true));
        segment.addItem(new WordItem("b", "x", true));
        parent.addItem(segment);
        assertEquals("x", segment.getIndexName());
        assertEquals("myArray.x", segment.getFieldName());
    }

    @Test
    void requireAllChildrenHaveNonEmptyTerm() {
        try {
            SameElementItem s = new SameElementItem("structa");
            s.addItem(new WordItem("", "f2"));
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) { // Success
            assertEquals("The word of a word item cannot be empty", e.getMessage());
        }
    }

}
