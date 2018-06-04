package com.yahoo.prelude.query.test;

import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.SameElementItem;
import com.yahoo.prelude.query.WordItem;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
    @Test(expected = IllegalArgumentException.class)
    public void requireAllChildrenHaveStructMemberNameSet() {
        SameElementItem s = new SameElementItem("structa");
        s.addItem(new WordItem("b", "f1"));
        s.addItem(new WordItem("c"));
    }
    @Test
    public void requireAllowCommonPrefix() {
        SameElementItem s = new SameElementItem("structa");
        s.addItem(new WordItem("b", "f1"));
        s.addItem(new WordItem("c", "structaf2"));
        assertEquals("structa:{f1:b structaf2:c}", s.toString());
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireNoChildrenHasCommonPrefixWithDot() {
        SameElementItem s = new SameElementItem("structa");
        s.addItem(new WordItem("b", "f1"));
        s.addItem(new WordItem("c", "structa.f2"));
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireAllChildrenHaveNonEmptyTerm() {
        SameElementItem s = new SameElementItem("structa");
        s.addItem(new WordItem("", "f2"));
    }
    @Test(expected = IllegalArgumentException.class)
    public void requireAllChildrenAreTermItems() {
        SameElementItem s = new SameElementItem("structa");
        s.addItem(new AndItem());
    }
}
