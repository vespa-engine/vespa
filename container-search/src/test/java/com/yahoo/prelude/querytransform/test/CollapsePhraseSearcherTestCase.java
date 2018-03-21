// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.querytransform.test;

import com.yahoo.search.Query;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.PhraseItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.prelude.querytransform.CollapsePhraseSearcher;
import com.yahoo.search.searchchain.Execution;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import static org.junit.Assert.assertEquals;

/**
 * Check CollapsePhraseSearcher works and only is triggered when it
 * should.
 *
 * @author Steinar Knutsen
 */
public class CollapsePhraseSearcherTestCase {

    @Test
    public void testSimplePositive() {
        PhraseItem root = new PhraseItem();
        root.addItem(new WordItem("abc"));
        assertEquals("abc", transformQuery(root));
    }

    @Test
    public void testPositive1() {
        AndItem root = new AndItem();
        root.addItem(new WordItem("a"));
        PhraseItem embedded = new PhraseItem();
        embedded.addItem(new WordItem("bcd"));
        root.addItem(embedded);
        root.addItem(new WordItem("e"));
        assertEquals("AND a bcd e",
                     transformQuery(root));
    }

    @Test
    public void testPositive2() {
        AndItem root = new AndItem();
        root.addItem(new WordItem("a"));
        CompositeItem embedded = new AndItem();
        embedded.addItem(new WordItem("bcd"));
        CompositeItem phrase = new PhraseItem();
        phrase.addItem(new WordItem("def"));
        embedded.addItem(phrase);
        root.addItem(embedded);
        root.addItem(new WordItem("e"));
        assertEquals("AND a (AND bcd def) e",
                     transformQuery(root));
    }

    @Test
    public void testNoTerms() {
        assertEquals("NULL", transformQuery("?query=" + enc("\"\"")));
    }

    @Test
    public void testNegative1() {
        assertEquals("\"abc def\"", transformQuery("?query=" + enc("\"abc def\"")));
    }

    @Test
    public void testNegative2() {
        assertEquals("AND a \"abc def\" b", transformQuery("?query=" + enc("a \"abc def\" b")));
    }

    private String enc(String s) {
        try {
            return URLEncoder.encode(s, "utf-8");
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testNegative3() {
        AndItem root = new AndItem();
        root.addItem(new WordItem("a"));
        CompositeItem embedded = new AndItem();
        embedded.addItem(new WordItem("bcd"));
        CompositeItem phrase = new PhraseItem();
        phrase.addItem(new WordItem("def"));
        phrase.addItem(new WordItem("ghi"));
        embedded.addItem(phrase);
        root.addItem(embedded);
        root.addItem(new WordItem("e"));
        assertEquals("AND a (AND bcd \"def ghi\") e",
                     transformQuery(root));
    }

    private String transformQuery(String rawQuery) {
        CollapsePhraseSearcher searcher = new CollapsePhraseSearcher();
        Query query = new Query(rawQuery);
        new Execution(searcher, Execution.Context.createContextStub()).search(query);
        Item newRoot = query.getModel().getQueryTree().getRoot();
        if (newRoot != null)
            return newRoot.toString();
        else
            return null;
    }

    private String transformQuery(Item queryTree) {
        CollapsePhraseSearcher searcher = new CollapsePhraseSearcher();
        Query query = new Query();
        query.getModel().getQueryTree().setRoot(queryTree);

        new Execution(searcher, Execution.Context.createContextStub()).search(query);
        Item newRoot = query.getModel().getQueryTree().getRoot();
        if (newRoot != null)
            return newRoot.toString();
        else
            return null;
    }

}
