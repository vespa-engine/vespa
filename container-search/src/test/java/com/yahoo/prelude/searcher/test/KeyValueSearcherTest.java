// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.searcher.test;

import com.yahoo.component.chain.Chain;
import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.document.GlobalId;
import com.yahoo.document.idstring.IdString;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.NullItem;
import com.yahoo.prelude.searcher.KeyValueSearcher;
import com.yahoo.prelude.searcher.KeyvalueConfig;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.Map.Entry;

import static org.junit.Assert.*;

public class KeyValueSearcherTest {

    private static class BackendMockup extends Searcher {
        private final Map<GlobalId, Entry<String, String>> dataMap;
        private final String summaryType;

        public BackendMockup(Map<GlobalId, Entry<String, String>> dataMap, String summaryType) {
            this.dataMap = dataMap;
            this.summaryType = summaryType;
        }

        @Override
        public Result search(Query query, Execution execution) {
            fail("Should not do search against backend");
            return null;
        }

        @Override
        public void fill(Result result, String summaryClass, Execution execution) {
            if (containsNullItem(result.getQuery().getModel().getQueryTree().getRoot()))
                fail("Got a query with a NullItem root. This cannot be encoded.");
            int numEmpty = 0;
            for (Hit hit : result.hits()) {
                FastHit fhit = (FastHit) hit;
                Entry<String, String> data = dataMap.get(fhit.getGlobalId());
                if (data != null) {
                    fhit.setField(data.getKey(), data.getValue());
                    fhit.setFilled(summaryType);
                } else {
                	numEmpty++;
                }
            }
            if (numEmpty > 0) {
            	result.hits().addError(ErrorMessage.createBackendCommunicationError("One or more hits were not filled"));
            }
        }
    }

    private Map<GlobalId, Entry<String,String>> dataMap;
    private BackendMockup backend;
    @Before
    public void setupBackend() {
        dataMap = new HashMap<>();
        dataMap.put(new GlobalId(IdString.createIdString("id:keyvalue:keyvalue::foo")), new AbstractMap.SimpleEntry<>("foo", "foovalue"));
        dataMap.put(new GlobalId(IdString.createIdString("id:keyvalue:keyvalue::bar")), new AbstractMap.SimpleEntry<>("bar", "barvalue"));
        dataMap.put(new GlobalId(IdString.createIdString("id:keyvalue:keyvalue::this_must_be_a_key_in_part1_fsadfasdfa")), new AbstractMap.SimpleEntry<>("this_must_be_a_key_in_part1_fsadfasdfa", "blabla"));
        backend = new BackendMockup(dataMap, "mysummary");
    }

    @Test
    public void testKeyValueSearcher() {
        Result result = executeQuery(getConfigString(1), "?keys=foo,bar");
        assertEquals(2, result.getTotalHitCount());
        for (Hit hit : result.hits()) {
        	FastHit fhit = (FastHit)hit;
        	Entry<String, String> data = dataMap.get(fhit.getGlobalId());
            assertEquals(data.getValue(), hit.getField(data.getKey()));
            assertTrue(hit.isFilled("mysummary"));
        }

        result = executeQuery(getConfigString(1),
                              "?keys=blabla,fofo", new BackendMockup(dataMap, "mysummary"));
        assertEquals(0, result.getTotalHitCount());

        result = executeQuery(getConfigString(1),
                              "?keys=non,foo,slsl", new BackendMockup(dataMap, "mysummary"));
        assertEquals(1, result.getTotalHitCount());
    }

    @Test
    public void testKeyValueSearcherWithNullItemAsQuery() {
        Query query = new Query("?keys=foo,bar");
        AndItem and = new AndItem();
        and.addItem(new NullItem());
        query.getModel().getQueryTree().setRoot(and);
        Result result = executeQuery(getConfigString(1), query);
        assertEquals(2, result.getTotalHitCount());
    }

    private static String getConfigString(int numRows) {
        return "raw:numparts 2\nsummaryName \"mysummary\"\ndocIdType \"keyvalue\"\ndocIdNameSpace \"keyvalue\"\nnumrows " + numRows + "\n";
    }

    @Test
    public void requireThatIgnoreRowBitsIsEnabledInGeneratedHits() {
        Result result = executeQuery(getConfigString(1),
                                     "?keys=foo,bar");
        for (Hit hit : result.hits()) {
            FastHit fastHit = (FastHit)hit;
            assertTrue(fastHit.shouldIgnoreRowBits());
        }
    }

    @Test
    public void requireThatNumRowsIsAPositiveNumber() {
        for (int i = -10; i < 1; ++i) {
            try {
                newKeyValueSearcher(getConfigString(i));
                fail();
            } catch (IllegalArgumentException e) {

            }
        }
        for (int i = 1; i < 10; ++i) {
            assertNotNull(newKeyValueSearcher(getConfigString(i)));
        }
    }

    @Test
    public void requireThatNumRowBitsAreCalculatedCorrectly() {
        assertRowBits(1, 0);
        assertRowBits(2, 1);
        assertRowBits(3, 2);
        assertRowBits(4, 2);
        assertRowBits(5, 3);
        assertRowBits(10, 4);
        assertRowBits(100, 7);
        assertRowBits(1000, 10);
    }

    private void assertRowBits(int numRows, int expectedNumRowBits) {
        Result result = executeQuery(getConfigString(numRows), "?keys=this_must_be_a_key_in_part1_fsadfasdfa");
        assertEquals(1, result.hits().size());
        FastHit hit = (FastHit)result.hits().get(0);
        assertEquals(0, hit.getPartId() & ((1 << expectedNumRowBits) - 1));
        assertEquals(1, hit.getPartId() >> expectedNumRowBits);
    }

    private Result executeQuery(String configId, String queryString, Searcher... searchers) {
        return executeQuery(configId, new Query(queryString), searchers);
    }

    private Result executeQuery(String configId, Query query, Searcher... searchers) {
        List<Searcher> chain = new LinkedList<>();
        chain.add(newKeyValueSearcher(configId));
        chain.addAll(Arrays.asList(searchers));
        chain.add(backend);
        return new Execution(new Chain<>(chain), Execution.Context.createContextStub()).search(query);
    }


    private static KeyValueSearcher newKeyValueSearcher(String configId) {
        return new KeyValueSearcher(new ConfigGetter<>(KeyvalueConfig.class).getConfig(configId));
    }

    private static boolean containsNullItem(Item item) {
        if (item instanceof NullItem) return true;
        if (item instanceof CompositeItem) {
            for (Iterator<Item> i = ((CompositeItem)item).getItemIterator(); i.hasNext(); )
                if (containsNullItem(i.next()))
                    return true;
        }
        return false;
    }

}
