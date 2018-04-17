// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.yahoo.container.protect.Error;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit testing of the searcher com.yahoo.search.querytransform.LegacyCombinator.
 *
 * @author Steinar Knutsen
 */
public class LegacyCombinatorTestCase {

    Searcher searcher;

    @Before
    public void setUp() throws Exception {
        searcher = new LegacyCombinator();
    }

    @Test
    public void testStraightForwardSearch() {
        Query q = new Query("?query=a&query.juhu=b");
        Execution e = new Execution(searcher, Execution.Context.createContextStub(new IndexFacts()));
        e.search(q);
        assertEquals("AND a b", q.getModel().getQueryTree().toString());
        q = new Query("?query=a&query.juhu=b&defidx.juhu=juhu.22[gnuff]");
        e = new Execution(searcher, Execution.Context.createContextStub(new IndexFacts()));
        e.search(q);
        assertEquals("AND a juhu.22[gnuff]:b", q.getModel().getQueryTree().toString());
        q = new Query("?query=a&query.juhu=");
        e = new Execution(searcher, Execution.Context.createContextStub(new IndexFacts()));
        e.search(q);
        assertEquals("a", q.getModel().getQueryTree().toString());
        q = new Query("?query=a+c&query.juhu=b");
        e = new Execution(searcher, Execution.Context.createContextStub(new IndexFacts()));
        e.search(q);
        assertEquals("AND a c b", q.getModel().getQueryTree().toString());
    }

    @Test
    public void testNoBaseQuery() {
        Query q = new Query("?query.juhu=b");
        Execution e = new Execution(searcher, Execution.Context.createContextStub(new IndexFacts()));
        e.search(q);
        assertEquals("b", q.getModel().getQueryTree().toString());
    }

    @Test
    public void testIncompatibleNewAndOldQuery() {
        Query q = new Query("?query.juhu=b&defidx.juhu=a&query.juhu.defidx=c");
        Execution e = new Execution(searcher, Execution.Context.createContextStub(new IndexFacts()));
        e.search(q);
        assertEquals("NULL", q.getModel().getQueryTree().toString());
        assertTrue("No expected error found.", q.errors().size() > 0);
        assertEquals("Did not get invalid query parameter error as expected.",
                Error.INVALID_QUERY_PARAMETER.code, q.errors().get(0).getCode());
    }

    @Test
    public void testNotCombinatorWithoutRoot() {
        Query q = new Query("?query.juhu=b&query.juhu.defidx=nalle&query.juhu.operator=not");
        Execution e = new Execution(searcher, Execution.Context.createContextStub(new IndexFacts()));
        e.search(q);
        assertEquals("NULL", q.getModel().getQueryTree().toString());
        assertTrue("No expected error found.", q.errors().size() > 0);
        System.out.println(q.errors());
        assertEquals("Did not get invalid query parameter error as expected.",
                Error.INVALID_QUERY_PARAMETER.code, q.errors().get(0).getCode());
    }

    @Test
    public void testRankCombinator() {
        Query q = new Query("?query.juhu=b&query.juhu.defidx=nalle&query.juhu.operator=rank");
        Execution e = new Execution(searcher, Execution.Context.createContextStub(new IndexFacts()));
        e.search(q);
        assertEquals("nalle:b", q.getModel().getQueryTree().toString());
    }

    @Test
    public void testRankAndNot() {
        Query q = new Query("?query.yahoo=2&query.yahoo.defidx=1&query.yahoo.operator=not&query.juhu=b&query.juhu.defidx=nalle&query.juhu.operator=rank");
        Execution e = new Execution(searcher, Execution.Context.createContextStub(new IndexFacts()));
        e.search(q);
        assertEquals("+nalle:b -1:2", q.getModel().getQueryTree().toString());
    }

    @Test
    public void testReqAndRankAndNot() {
        Query q = new Query("?query.yahoo=2&query.yahoo.defidx=1&query.yahoo.operator=not&query.juhu=b&query.juhu.defidx=nalle&query.juhu.operator=rank&query.bamse=z&query.bamse.defidx=y");
        Execution e = new Execution(searcher, Execution.Context.createContextStub(new IndexFacts()));
        e.search(q);
        assertEquals("+(RANK y:z nalle:b) -1:2", q.getModel().getQueryTree().toString());
    }

    @Test
    public void testReqAndRank() {
        Query q = new Query("?query.juhu=b&query.juhu.defidx=nalle&query.juhu.operator=rank&query.bamse=z&query.bamse.defidx=y");
        Execution e = new Execution(searcher, Execution.Context.createContextStub(new IndexFacts()));
        e.search(q);
        assertEquals("RANK y:z nalle:b", q.getModel().getQueryTree().toString());
    }

    @Test
    public void testReqAndNot() {
        Query q = new Query("?query.juhu=b&query.juhu.defidx=nalle&query.juhu.operator=not&query.bamse=z&query.bamse.defidx=y");
        Execution e = new Execution(searcher, Execution.Context.createContextStub(new IndexFacts()));
        e.search(q);
        assertEquals("+y:z -nalle:b", q.getModel().getQueryTree().toString());
    }

    @Test
    public void testNewAndOld() {
        Query q = new Query("?query.juhu=b&defidx.juhu=nalle&query.bamse=z&query.bamse.defidx=y");
        Execution e = new Execution(searcher, Execution.Context.createContextStub(new IndexFacts()));
        Set<StringPair> nastierItems = new HashSet<>();
        nastierItems.add(new StringPair("nalle", "b"));
        nastierItems.add(new StringPair("y", "z"));
        e.search(q);
        AndItem root = (AndItem) q.getModel().getQueryTree().getRoot();
        Iterator<?> iterator = root.getItemIterator();
        while (iterator.hasNext()) {
            WordItem word = (WordItem) iterator.next();
            StringPair asPair = new StringPair(word.getIndexName(), word.stringValue());
            if (nastierItems.contains(asPair)) {
                nastierItems.remove(asPair);
            } else {
                assertFalse("Got unexpected item in query tree: ("
                            + word.getIndexName() + ", " + word.stringValue() + ")",
                           true);
            }
        }
        assertEquals("Not all expected items found in query.", 0, nastierItems.size());
    }

    @Test
    public void testReqAndNotWithQuerySyntaxAll() {
        Query q = new Query("?query.juhu=b+c&query.juhu.defidx=nalle&query.juhu.operator=not&query.juhu.type=any&query.bamse=z&query.bamse.defidx=y");
        Execution e = new Execution(searcher, Execution.Context.createContextStub(new IndexFacts()));
        e.search(q);
        assertEquals("+y:z -(OR nalle:b nalle:c)", q.getModel().getQueryTree().toString());
    }

    @Test
    public void testDefaultIndexWithoutQuery() {
        Query q = new Query("?defidx.juhu=b");
        Execution e = new Execution(searcher, Execution.Context.createContextStub(new IndexFacts()));
        e.search(q);
        assertEquals("NULL", q.getModel().getQueryTree().toString());
        q = new Query("?query=a&defidx.juhu=b");
        e = new Execution(searcher, Execution.Context.createContextStub(new IndexFacts()));
        e.search(q);
        assertEquals("a", q.getModel().getQueryTree().toString());
    }

    private static class StringPair {

        public final String index;
        public final String value;

        StringPair(String index, String value) {
            super();
            this.index = index;
            this.value = value;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((index == null) ? 0 : index.hashCode());
            result = prime * result + ((value == null) ? 0 : value.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final StringPair other = (StringPair) obj;
            if (index == null) {
                if (other.index != null)
                    return false;
            } else if (!index.equals(other.index))
                return false;
            if (value == null) {
                if (other.value != null)
                    return false;
            } else if (!value.equals(other.value))
                return false;
            return true;
        }

    }

    @Test
    public void testMultiPart() {
        Query q = new Query("?query=a&query.juhu=b&query.nalle=c");
        Execution e = new Execution(searcher, Execution.Context.createContextStub(new IndexFacts()));
        Set<String> items = new HashSet<>();
        items.add("a");
        items.add("b");
        items.add("c");
        e.search(q);
        // OK, the problem here is we have no way of knowing whether nalle or
        // juhu was added first, since we have passed through HashMap instances
        // inside the implementation

        AndItem root = (AndItem) q.getModel().getQueryTree().getRoot();
        Iterator<?> iterator = root.getItemIterator();
        while (iterator.hasNext()) {
            WordItem word = (WordItem) iterator.next();
            if (items.contains(word.stringValue())) {
                items.remove(word.stringValue());
            } else {
                assertFalse("Got unexpected item in query tree: " + word.stringValue(), true);
            }
        }
        assertEquals("Not all expected items found in query.", 0, items.size());

        Set<StringPair> nastierItems = new HashSet<>();
        nastierItems.add(new StringPair("", "a"));
        nastierItems.add(new StringPair("juhu.22[gnuff]", "b"));
        nastierItems.add(new StringPair("gnuff[8].name(\"tralala\")", "c"));
        q = new Query("?query=a&query.juhu=b&defidx.juhu=juhu.22[gnuff]&query.nalle=c&defidx.nalle=gnuff[8].name(%22tralala%22)");
        e = new Execution(searcher, Execution.Context.createContextStub(new IndexFacts()));
        e.search(q);
        root = (AndItem) q.getModel().getQueryTree().getRoot();
        iterator = root.getItemIterator();
        while (iterator.hasNext()) {
            WordItem word = (WordItem) iterator.next();
            StringPair asPair = new StringPair(word.getIndexName(), word.stringValue());
            if (nastierItems.contains(asPair)) {
                nastierItems.remove(asPair);
            } else {
                assertFalse("Got unexpected item in query tree: ("
                        + word.getIndexName() + ", " + word.stringValue() + ")",
                        true);
            }
        }
        assertEquals("Not all expected items found in query.", 0, nastierItems.size());
    }

}
