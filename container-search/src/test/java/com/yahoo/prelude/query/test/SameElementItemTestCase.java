// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

}
