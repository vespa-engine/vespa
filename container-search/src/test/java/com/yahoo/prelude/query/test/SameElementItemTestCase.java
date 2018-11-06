// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.test;

import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.SameElementItem;
import com.yahoo.prelude.query.WordItem;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SameElementItemTestCase {

    @Test
    public void testAddItem() {
        SameElementItem s = new SameElementItem("structa");
        s.addItem(new WordItem("b", "f1"));
        s.addItem(new WordItem("c", "f2"));
        s.addItem(new WordItem("d", "f3"));
        assertEquals("structa:{f1:b f2:c f3:d}", s.toString());
    }

    @Test
    public void testClone() {
        SameElementItem s = new SameElementItem("structa");
        s.addItem(new WordItem("b", "f1"));
        s.addItem(new WordItem("c", "f2"));
        s.addItem(new WordItem("d", "f3"));
        assertEquals("structa:{f1:b f2:c f3:d}", s.toString());
        SameElementItem c = (SameElementItem)s.clone();
        assertEquals("structa:{f1:b f2:c f3:d}", c.toString());
    }

    @Test
    public void requireAllChildrenHaveStructMemberNameSet() {
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
    public void requireAllowCommonPrefix() {
        SameElementItem s = new SameElementItem("structa");
        s.addItem(new WordItem("b", "f1"));
        s.addItem(new WordItem("c", "structaf2"));
        assertEquals("structa:{f1:b structaf2:c}", s.toString());
    }

    @Test
    public void requireChildrenCanHavePrefixCommonWithParent() {
        SameElementItem s = new SameElementItem("structa");
        s.addItem(new WordItem("b", "f1"));
        s.addItem(new WordItem("c", "structa.f2"));
        assertEquals("structa:{f1:b structa.f2:c}", s.toString());
    }

    @Test
    public void requireAllChildrenHaveNonEmptyTerm() {
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
    public void requireAllChildrenAreTermItems() {
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

}
