// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.test;

import com.google.common.collect.ImmutableList;
import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.language.process.StemMode;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.SearchDefinition;
import com.yahoo.search.Query;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.search.searchchain.Execution;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests using synthetic index names for IndexFacts class.
 *
 * @author Steinar Knutsen
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class IndexFactsTestCase {

    private static final String INDEXFACTS_TESTING = "file:src/test/java/com/yahoo/prelude/test/indexfactstesting.cfg";

    @SuppressWarnings("deprecation")
    private IndexFacts createIndexFacts() {
        ConfigGetter<IndexInfoConfig> getter = new ConfigGetter<>(IndexInfoConfig.class);
        IndexInfoConfig config = getter.getConfig(INDEXFACTS_TESTING);
        return new IndexFacts(new IndexModel(config, createClusters()));
    }

    private IndexFacts createIndexFacts(Collection<SearchDefinition> searchDefinitions) {
        return new IndexFacts(new IndexModel(createClusters(), searchDefinitions));
    }

    private Map<String, List<String>> createClusters() {
        List<String> clusterOne = new ArrayList<>();
        List<String> clusterTwo = new ArrayList<>();
        clusterOne.addAll(Arrays.asList("one", "two"));
        clusterTwo.addAll(Arrays.asList("one", "three"));
        Map<String, List<String>> clusters = new HashMap<>();
        clusters.put("clusterOne", clusterOne);
        clusters.put("clusterTwo", clusterTwo);
        return clusters;
    }

    @Test
    void testBasicCases() {
        // First check default behavior
        IndexFacts indexFacts = createIndexFacts();
        Query q = newQuery("?query=a:b", indexFacts);
        assertEquals("WEAKAND(100) a:b", q.getModel().getQueryTree().getRoot().toString());
        q = newQuery("?query=notarealindex:b", indexFacts);
        assertEquals("WEAKAND(100) (AND notarealindex b)",  q.getModel().getQueryTree().getRoot().toString());
    }

    @Test
    void testDefaultPosition() {
        Index a = new Index("a");
        assertFalse(a.isDefaultPosition());
        a.addCommand("any");
        assertFalse(a.isDefaultPosition());
        a.addCommand("default-position");
        assertTrue(a.isDefaultPosition());

        SearchDefinition sd = new SearchDefinition("sd");
        sd.addCommand("b", "any");
        assertNull(sd.getDefaultPosition());
        sd.addCommand("c", "default-position");
        assertEquals(sd.getDefaultPosition(), "c");

        SearchDefinition sd2 = new SearchDefinition("sd2");
        sd2.addIndex(new Index("b").addCommand("any"));
        assertNull(sd2.getDefaultPosition());
        sd2.addIndex(a);
        assertEquals(sd2.getDefaultPosition(), "a");

        IndexFacts indexFacts = createIndexFacts(ImmutableList.of(sd, sd2));
        assertEquals(indexFacts.getDefaultPosition(null), "a");
        assertEquals(indexFacts.getDefaultPosition("sd"), "c");
    }

    @Test
    void testIndicesInAnyConfigurationAreIndicesInDefault() {
        IndexFacts.Session indexFacts = createIndexFacts().newSession(new Query());
        assertTrue(indexFacts.isIndex("a"));
        assertTrue(indexFacts.isIndex("b"));
        assertTrue(indexFacts.isIndex("c"));
        assertTrue(indexFacts.isIndex("d"));
        assertFalse(indexFacts.isIndex("anythingelse"));
    }

    @Test
    void testDefaultIsUnionHostIndex() {
        IndexFacts.Session session = createIndexFacts().newSession(new Query());
        assertTrue(session.getIndex("c").isHostIndex());
        assertFalse(session.getIndex("a").isHostIndex());
    }

    @Test
    void testDefaultIsUnionUriIndex() {
        IndexFacts indexFacts = createIndexFacts();
        assertTrue(indexFacts.newSession(new Query()).getIndex("d").isUriIndex());
        assertFalse(indexFacts.newSession(new Query()).getIndex("a").isUriIndex());
    }

    @Test
    void testDefaultIsUnionStemMode() {
        IndexFacts.Session session = createIndexFacts().newSession(new Query());
        assertEquals(StemMode.NONE, session.getIndex("a").getStemMode());
        assertEquals(StemMode.NONE, session.getIndex("b").getStemMode());
    }

    private void assertExactIsWorking(String indexName) {
        SearchDefinition sd = new SearchDefinition("artist");

        Index index = new Index(indexName);
        index.setExact(true,"^^^");
        sd.addIndex(index);

        IndexFacts indexFacts = new IndexFacts(new IndexModel(sd));
        Query query = new Query();
        query.getModel().getSources().add("artist");
        assertTrue(indexFacts.newSession(query).getIndex(indexName).isExact());
        Query q = newQuery("?query=" + indexName + ":foo...&search=artist&type=all", indexFacts);
        assertEquals(indexName + ":foo...", q.getModel().getQueryTree().getRoot().toString());
    }

    @Test
    void testExactMatching() {
        assertExactIsWorking("test");
        assertExactIsWorking("artist_name_ft_norm1");

        List search = new ArrayList();
        search.add("three");
        Query query = new Query();
        query.getModel().getSources().add("three");
        IndexFacts.Session threeSession = createIndexFacts().newSession(query);
        IndexFacts.Session nullSession = createIndexFacts().newSession(new Query());

        Index d3 = threeSession.getIndex("d");
        assertTrue(d3.isExact());
        assertEquals(" ", d3.getExactTerminator());

        Index e = nullSession.getIndex("e");
        assertTrue(e.isExact());
        assertEquals("kj(/&", e.getExactTerminator());

        Index a = nullSession.getIndex("a");
        assertFalse(a.isExact());
        assertNull(a.getExactTerminator());

        Index wem = threeSession.getIndex("twewm");
        assertTrue(wem.isExact());
        assertNull(wem.getExactTerminator());
    }

    @Test
    void testComplexExactMatching() {
        SearchDefinition sd = new SearchDefinition("foobar");
        String u_name = "foo_bar";
        Index u_index = new Index(u_name);
        u_index.setExact(true, "^^^");
        Index b_index = new Index("bar");
        sd.addIndex(u_index);
        sd.addIndex(b_index);

        IndexFacts indexFacts = new IndexFacts(new IndexModel(sd));
        Query query = new Query();
        query.getModel().getSources().add("foobar");
        IndexFacts.Session session = indexFacts.newSession(query);
        assertFalse(session.getIndex("bar").isExact());
        assertTrue(session.getIndex(u_name).isExact());
        Query q = newQuery("?query=" + u_name + ":foo...&search=foobar&type=all", indexFacts);
        assertEquals(u_name + ":foo...", q.getModel().getQueryTree().getRoot().toString());
    }

    // This is also backed by a system test on cause of complex config
    @Test
    void testRestrictLists1() {
        Query query = new Query();
        query.getModel().getSources().add("nalle");
        query.getModel().getSources().add("one");
        query.getModel().getRestrict().add("two");

        IndexFacts.Session indexFacts = createIndexFacts().newSession(Collections.singleton("clusterOne"), Collections.emptyList());
        assertTrue(indexFacts.isIndex("a"));
        assertFalse(indexFacts.isIndex("b"));
        assertTrue(indexFacts.isIndex("d"));
    }

    @Test
    void testRestrictLists2() {
        Query query = new Query();
        query.getModel().getSources().add("clusterTwo");
        query.getModel().getRestrict().add("three");
        IndexFacts indexFacts = createIndexFacts();
        IndexFacts.Session session = indexFacts.newSession(query);
        assertFalse(session.getIndex("c").isNull());
        assertTrue(session.getIndex("e").isNull());
        assertEquals("c", session.getCanonicName("C"));
        assertTrue(session.getIndex("c").isHostIndex());
        assertFalse(session.getIndex("a").isNull());
        assertFalse(session.getIndex("a").isHostIndex());
        assertEquals(StemMode.SHORTEST, session.getIndex("a").getStemMode());
        assertFalse(session.getIndex("b").isNull());
        assertFalse(session.getIndex("b").isUriIndex());
        assertFalse(session.getIndex("b").isHostIndex());
        assertEquals(StemMode.NONE, session.getIndex("b").getStemMode());
    }

    @Test
    void testRestrictLists3() {
        Query query = new Query();
        query.getModel().getSources().add("clusterOne");
        query.getModel().getRestrict().add("two");
        IndexFacts indexFacts = createIndexFacts();
        IndexFacts.Session session = indexFacts.newSession(query);
        assertTrue(session.getIndex("a").isNull());
        assertFalse(session.getIndex("d").isNull());
        assertTrue(session.getIndex("d").isUriIndex());
        assertTrue(session.getIndex("e").isExact());
    }

    private Query newQuery(String queryString, IndexFacts indexFacts) {
        Query query = new Query(queryString);
        query.getModel().setExecution(new Execution(Execution.Context.createContextStub(indexFacts)));
        return query;
    }

    @Test
    void testPredicateBounds() {
        Index index = new Index("a");
        assertEquals(Long.MIN_VALUE, index.getPredicateLowerBound());
        assertEquals(Long.MAX_VALUE, index.getPredicateUpperBound());
        index.addCommand("predicate-bounds [2..300]");
        assertEquals(2L, index.getPredicateLowerBound());
        assertEquals(300L, index.getPredicateUpperBound());
        index.addCommand("predicate-bounds [-20000..30000]");
        assertEquals(-20_000L, index.getPredicateLowerBound());
        assertEquals(30_000L, index.getPredicateUpperBound());
        index.addCommand("predicate-bounds [-40000000000..-300]");
        assertEquals(-40_000_000_000L, index.getPredicateLowerBound());
        assertEquals(-300L, index.getPredicateUpperBound());
        index.addCommand("predicate-bounds [..300]");
        assertEquals(Long.MIN_VALUE, index.getPredicateLowerBound());
        assertEquals(300L, index.getPredicateUpperBound());
        index.addCommand("predicate-bounds [2..]");
        assertEquals(2L, index.getPredicateLowerBound());
        assertEquals(Long.MAX_VALUE, index.getPredicateUpperBound());
    }

    @Test
    void testUriIndexAndRestrict() {
        IndexInfoConfig.Builder b = new IndexInfoConfig.Builder();

        IndexInfoConfig.Indexinfo.Builder b1 = new IndexInfoConfig.Indexinfo.Builder();
        b1.name("hasUri");
        IndexInfoConfig.Indexinfo.Command.Builder bb1 = new IndexInfoConfig.Indexinfo.Command.Builder();
        bb1.indexname("url");
        bb1.command("fullurl");
        b1.command(bb1);
        b.indexinfo(b1);

        IndexInfoConfig.Indexinfo.Builder b2 = new IndexInfoConfig.Indexinfo.Builder();
        b2.name("hasNotUri1");
        b.indexinfo(b2);

        IndexInfoConfig.Indexinfo.Builder b3 = new IndexInfoConfig.Indexinfo.Builder();
        b3.name("hasNotUri2");
        b.indexinfo(b3);

        IndexInfoConfig config = new IndexInfoConfig(b);
        IndexFacts indexFacts = new IndexFacts(new IndexModel(config, Collections.emptyMap()));
        Query query1 = new Query("?query=url:https://foo.bar");
        Query query2 = new Query("?query=url:https://foo.bar&restrict=hasUri");
        assertEquals(0, query1.getModel().getRestrict().size());
        assertEquals(1, query2.getModel().getRestrict().size());
        IndexFacts.Session session1 = indexFacts.newSession(query1.getModel().getSources(), query1.getModel().getRestrict());
        IndexFacts.Session session2 = indexFacts.newSession(query2.getModel().getSources(), query2.getModel().getRestrict());
        assertTrue(session1.getIndex("url").isUriIndex());
        assertTrue(session2.getIndex("url").isUriIndex());
        assertEquals("WEAKAND(100) (AND url:https url:foo url:bar)", query1.getModel().getQueryTree().toString());
        assertEquals("WEAKAND(100) (AND url:https url:foo url:bar)", query2.getModel().getQueryTree().toString());
    }

    @Test
    void testConflictingAliases() {
        SearchDefinition first = new SearchDefinition("first");
        Index field1 = new Index("field1");
        first.addIndex(field1);

        SearchDefinition second = new SearchDefinition("second");
        Index field2 = new Index("field2");
        field2.addAlias("field1");
        second.addIndex(field2);

        // Alias to field1 conflics with field1 in the "union" search definition.
        // Should not produce an exception (but a log message):
        new IndexFacts(new IndexModel(Collections.emptyMap(), ImmutableList.of(first, second)));
    }
    
}
