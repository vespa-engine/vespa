// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.querytransform.test;

import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.NotItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.prelude.querytransform.QueryRewrite;
import com.yahoo.search.Query;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author baldersheim
 */
public class QueryRewriteTestCase {

    @Test
    public void requireThatOptimizeByRestrictSimplifiesORItemsThatHaveFullRecall() {
        assertRewritten("sddocname:foo OR sddocname:bar OR sddocname:baz", "foo", "sddocname:foo");
        assertRewritten("sddocname:foo OR sddocname:bar OR sddocname:baz", "bar", "sddocname:bar");
        assertRewritten("sddocname:foo OR sddocname:bar OR sddocname:baz", "baz", "sddocname:baz");

        assertRewritten("lhs OR (sddocname:foo OR sddocname:bar OR sddocname:baz)", "foo", "sddocname:foo");
        assertRewritten("lhs OR (sddocname:foo OR sddocname:bar OR sddocname:baz)", "bar", "sddocname:bar");
        assertRewritten("lhs OR (sddocname:foo OR sddocname:bar OR sddocname:baz)", "baz", "sddocname:baz");

        assertRewritten("lhs AND (sddocname:foo OR sddocname:bar OR sddocname:baz)", "foo", "lhs");
        assertRewritten("lhs AND (sddocname:foo OR sddocname:bar OR sddocname:baz)", "bar", "lhs");
        assertRewritten("lhs AND (sddocname:foo OR sddocname:bar OR sddocname:baz)", "baz", "lhs");
    }

    @Test
    public void requireThatOptimizeByRestrictSimplifiesANDItemsThatHaveZeroRecall() {
        assertRewritten("sddocname:foo AND bar AND baz", "cox", "NULL");
        assertRewritten("foo AND sddocname:bar AND baz", "cox", "NULL");
        assertRewritten("foo AND bar AND sddocname:baz", "cox", "NULL");

        assertRewritten("lhs AND (sddocname:foo AND bar AND baz)", "cox", "NULL");
        assertRewritten("lhs AND (foo AND sddocname:bar AND baz)", "cox", "NULL");
        assertRewritten("lhs AND (foo AND bar AND sddocname:baz)", "cox", "NULL");

        assertRewritten("lhs OR (sddocname:foo AND bar AND baz)", "cox", "lhs");
        assertRewritten("lhs OR (foo AND sddocname:bar AND baz)", "cox", "lhs");
        assertRewritten("lhs OR (foo AND bar AND sddocname:baz)", "cox", "lhs");
    }

    @Test
    public void testRestrictRewrite() {
        assertRewritten("a AND b", "per", "AND a b");
        assertRewritten("a OR b", "per", "OR a b");
        assertRewritten("sddocname:per", "per", "sddocname:per");
        assertRewritten("sddocname:per", "espen", "NULL");
        assertRewritten("sddocname:per OR sddocname:peder", "per", "sddocname:per");
        assertRewritten("sddocname:per AND sddocname:peder", "per", "NULL");
        assertRewritten("(sddocname:per AND a) OR (sddocname:peder AND b)", "per", "a");
        assertRewritten("sddocname:per ANDNOT b", "per", "+sddocname:per -b");
        assertRewritten("sddocname:perder ANDNOT b", "per", "NULL");
        assertRewritten("a ANDNOT sddocname:per a b", "per", "NULL");
    }

    @Test
    public void testRestrictRank() {
        assertRewritten("sddocname:per&filter=abc", "espen", "|abc");
        assertRewritten("sddocname:per&filter=abc", "per", "RANK sddocname:per |abc");
    }

    private static void assertRewritten(String queryParam, String restrictParam, String expectedOptimizedQuery) {
        Query query = new Query("?type=adv&query=" + queryParam.replace(" ", "%20") + "&restrict=" + restrictParam);
        QueryRewrite.optimizeByRestrict(query);
        QueryRewrite.collapseSingleComposites(query);
        assertEquals(expectedOptimizedQuery, query.getModel().getQueryTree().toString());
    }

    @Test
    public void assertAndNotMovedUp() {
        Query query = new Query();
        NotItem not = new NotItem();
        not.addPositiveItem(new WordItem("a"));
        not.addNegativeItem(new WordItem("na"));
        AndItem and = new AndItem();
        and.addItem(not);
        query.getModel().getQueryTree().setRoot(and);
        QueryRewrite.optimizeAndNot(query);
        assertTrue(query.getModel().getQueryTree().getRoot() instanceof NotItem);
        NotItem n = (NotItem) query.getModel().getQueryTree().getRoot();
        assertEquals(2, n.getItemCount());
        assertTrue(n.getPositiveItem() instanceof AndItem);
        AndItem a = (AndItem) n.getPositiveItem();
        assertEquals(1, a.getItemCount());
        assertEquals("a", a.getItem(0).toString());
        assertEquals("na", n.getItem(1).toString());
    }

    @Test
    public void assertMultipleAndNotIsCollapsed() {
        Query query = new Query();
        NotItem not1 = new NotItem();
        not1.addPositiveItem(new WordItem("a"));
        not1.addNegativeItem(new WordItem("na1"));
        not1.addNegativeItem(new WordItem("na2"));
        NotItem not2 = new NotItem();
        not2.addPositiveItem(new WordItem("b"));
        not2.addNegativeItem(new WordItem("nb"));
        AndItem and = new AndItem();
        and.addItem(new WordItem("1"));
        and.addItem(not1);
        and.addItem(new WordItem("2"));
        and.addItem(not2);
        and.addItem(new WordItem("3"));
        query.getModel().getQueryTree().setRoot(and);

        QueryRewrite.optimizeAndNot(query);

        assertTrue(query.getModel().getQueryTree().getRoot() instanceof NotItem);
        NotItem n = (NotItem) query.getModel().getQueryTree().getRoot();
        assertTrue(n.getPositiveItem() instanceof AndItem);
        assertEquals(4, n.getItemCount());
        AndItem a = (AndItem) n.getPositiveItem();
        assertEquals(5, a.getItemCount());
        assertEquals("na1", n.getItem(1).toString());
        assertEquals("na2",n.getItem(2).toString());
        assertEquals("nb", n.getItem(3).toString());
        assertEquals("1", a.getItem(0).toString());
        assertEquals("a", a.getItem(1).toString());
        assertEquals("2", a.getItem(2).toString());
        assertEquals("b", a.getItem(3).toString());
        assertEquals("3", a.getItem(4).toString());
    }

}
