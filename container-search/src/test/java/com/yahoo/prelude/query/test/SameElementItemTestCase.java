// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.test;

import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.IntItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.SameElementItem;
import com.yahoo.prelude.query.Substring;
import com.yahoo.prelude.query.TermItem;
import com.yahoo.prelude.query.WordAlternativesItem;
import com.yahoo.prelude.query.WordItem;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

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
    void requireAllChildrenHaveStructMemberNameSet() {
        try {
            SameElementItem s = new SameElementItem("structa");
            s.addItem(new WordItem("b", "f1"));
            s.addItem(new WordItem("c"));
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) { // success
            assertEquals("Struct fieldname can not be empty", e.getMessage());
        }
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
    void requireAllChildrenHaveNonEmptyTerm() {
        try {
            SameElementItem s = new SameElementItem("structa");
            s.addItem(new WordItem("", "f2"));
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) { // Success
            assertEquals("The word of a word item can not be empty", e.getMessage());
        }
    }

    @Test
    void requireNoChildrenAreWordAlternatives() {
        try {
            SameElementItem s = new SameElementItem("structa");
            s.addItem(new AndItem());
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) { // Success
            assertEquals("Child item (AND ) should be an instance of class com.yahoo.prelude.query.TermItem but is class com.yahoo.prelude.query.AndItem",
                    e.getMessage());
        }
    }

    @Test
    void requireAllChildrenAreTermItems() {
        try {
            SameElementItem s = new SameElementItem("structa");
            s.addItem(new WordAlternativesItem("test", true, new Substring("origin"), List.of(new WordAlternativesItem.Alternative("a", 0.3))));
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) { // Success
            assertEquals("Child item WORD_ALTERNATIVES test:[ a(0.3) ] should NOT be an instance of class com.yahoo.prelude.query.WordAlternativesItem but is class com.yahoo.prelude.query.WordAlternativesItem",
                    e.getMessage());
        }
    }

    private void verifyExtractSingle(TermItem term) {
        String subFieldName = term.getIndexName();
        SameElementItem s = new SameElementItem("structa");
        s.addItem(term);
        Optional<Item> single =s.extractSingleChild();
        assertTrue(single.isPresent());
        assertEquals(((TermItem)single.get()).getIndexName(), s.getFieldName() + "." + subFieldName);
    }

    @Test
    void requireExtractSingleItemToExtractSingles() {
        verifyExtractSingle(new WordItem("b", "f1"));
        verifyExtractSingle(new IntItem("7", "f1"));
    }

    @Test
    void requireExtractSingleItemToExtractSinglesOnly() {
        SameElementItem s = new SameElementItem("structa");
        s.addItem(new WordItem("b", "f1"));
        s.addItem(new WordItem("c", "f2"));
        assertTrue(s.extractSingleChild().isEmpty());
    }

}
