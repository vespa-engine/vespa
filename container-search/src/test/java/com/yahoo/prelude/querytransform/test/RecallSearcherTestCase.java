// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.querytransform.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

import com.yahoo.prelude.IndexFacts;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.NullItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.prelude.querytransform.RecallSearcher;
import com.yahoo.search.searchchain.Execution;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen
 */
public class RecallSearcherTestCase {

    @Test
    public void testIgnoreEmptyProperty() {
        RecallSearcher searcher = new RecallSearcher();
        Query query = new Query();
        Result result = new Execution(searcher, Execution.Context.createContextStub()).search(query);
        assertNull(result.hits().getError());
        assertTrue(query.getModel().getQueryTree().getRoot() instanceof NullItem);
    }

    @Test
    public void testDenyRankItems() {
        RecallSearcher searcher = new RecallSearcher();
        Query query = new Query("?recall=foo");
        Result result = new Execution(searcher, Execution.Context.createContextStub(new IndexFacts())).search(query);
        assertNotNull(result.hits().getError());
    }

    @Test
    public void testParse() {
        List<String> empty = new ArrayList<>();
        assertQueryTree("?query=foo", Arrays.asList("foo"), empty);
        assertQueryTree("?recall=%2bfoo", empty, Arrays.asList("foo"));
        assertQueryTree("?query=foo&filter=bar&recall=%2bbaz", Arrays.asList("foo", "bar"), Arrays.asList("baz"));
        assertQueryTree("?query=foo+bar&filter=baz&recall=%2bcox", Arrays.asList("foo", "bar", "baz"), Arrays.asList("cox"));
        assertQueryTree("?query=foo&filter=bar+baz&recall=%2bcox", Arrays.asList("foo", "bar", "baz"), Arrays.asList("cox"));
        assertQueryTree("?query=foo&filter=bar&recall=-baz+%2bcox", Arrays.asList("foo", "bar"), Arrays.asList("baz", "cox"));
        assertQueryTree("?query=foo%20bar&recall=%2bbaz%20-cox", Arrays.asList("foo", "bar"), Arrays.asList("baz", "cox"));
    }

    private static void assertQueryTree(String query, List<String> ranked, List<String> unranked) {
        RecallSearcher searcher = new RecallSearcher();
        Query obj = new Query(query);
        Result result = new Execution(searcher, Execution.Context.createContextStub(new IndexFacts())).search(obj);
        if (result.hits().getError() != null) {
            fail(result.hits().getError().toString());
        }

        List<String> myRanked = new ArrayList<>(ranked);
        List<String> myUnranked = new ArrayList<>(unranked);

        Stack<Item> stack = new Stack<>();
        stack.push(obj.getModel().getQueryTree().getRoot());
        while (!stack.isEmpty()) {
            Item item = stack.pop();
            if (item instanceof WordItem) {
                String word = ((WordItem)item).getWord();
                if (item.isRanked()) {
                    int idx = myRanked.indexOf(word);
                    if (idx < 0) {
                        fail("Term '" + word + "' not expected as ranked term.");
                    }
                    myRanked.remove(idx);
                } else {
                    int idx = myUnranked.indexOf(word);
                    if (idx < 0) {
                        fail("Term '" + word + "' not expected as unranked term.");
                    }
                    myUnranked.remove(idx);
                }
            }
            if (item instanceof CompositeItem) {
                CompositeItem lst = (CompositeItem)item;
                for (Iterator<?> it = lst.getItemIterator(); it.hasNext();) {
                    stack.push((Item)it.next());
                }
            }
        }

        if (!myRanked.isEmpty()) {
            fail("Ranked terms " + myRanked + " not found.");
        }
        if (!myUnranked.isEmpty()) {
            fail("Unranked terms " + myUnranked + " not found.");
        }
    }

}
