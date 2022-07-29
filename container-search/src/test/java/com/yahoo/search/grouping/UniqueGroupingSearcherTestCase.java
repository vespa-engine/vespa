// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping;

import com.yahoo.component.chain.Chain;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.grouping.result.Group;
import com.yahoo.search.grouping.result.GroupList;
import com.yahoo.search.grouping.result.HitList;
import com.yahoo.search.grouping.result.RootGroup;
import com.yahoo.search.grouping.result.StringId;
import com.yahoo.search.query.Sorting;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.Relevance;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.yolean.Exceptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author andreer
 */
public class UniqueGroupingSearcherTestCase {

    @Test
    void testSkipGroupingBasedDedup() {
        Result result = search("?query=foo",
                new MockResultProvider(0, false));
        assertEquals(0, result.hits().size());
    }

    @Test
    void testSkipGroupingBasedDedupIfMultiLevelSorting() {
        Result result = search("?query=foo&unique=fingerprint&sorting=-pubdate%20-[rank]",
                new MockResultProvider(0, false));
        assertEquals(0, result.hits().size());
    }

    @Test
    void testIllegalSortingSpec() {
        try {
            search("?query=foo&unique=fingerprint&sorting=-1",
                    new MockResultProvider(0, true).addGroupList(new GroupList("fingerprint")));
            fail("Above statement should throw");
        } catch (IllegalArgumentException e) {
            // As expected.
            assertTrue(Exceptions.toMessageString(e).contains("Could not set 'ranking.sorting' to '-1': " +
                    "Illegal attribute name '1' for sorting. " +
                    "Requires '[\\[]*[a-zA-Z_][\\.a-zA-Z0-9_-]*[\\]]*'"));
        }
    }

    @Test
    void testGroupingBasedDedupNoGroupingHits() {
        Result result = search("?query=foo&unique=fingerprint",
                new MockResultProvider(0, true));
        assertEquals(0, result.hits().size());
    }

    @Test
    void testGroupingBasedDedupWithEmptyGroupingHitsList() {
        Result result = search("?query=foo&unique=fingerprint",
                new MockResultProvider(0, true).addGroupList(new GroupList("fingerprint")));
        assertEquals(0, result.hits().size());
        assertEquals(0, result.getTotalHitCount());
    }

    @Test
    void testGroupingBasedDedupWithNullGroupingResult() {
        try {
            search("?query=foo&unique=fingerprint",
                    new MockResultProvider(0, false));
            fail();
        } catch (IllegalStateException e) {
            assertEquals("Failed to produce deduped result set.", e.getMessage());
        }
    }

    @Test
    void testGroupingBasedDedupWithGroupingHits() {
        GroupList fingerprint = new GroupList("fingerprint");
        fingerprint.add(makeHitGroup("1"));
        fingerprint.add(makeHitGroup("2"));
        fingerprint.add(makeHitGroup("3"));
        fingerprint.add(makeHitGroup("4"));
        fingerprint.add(makeHitGroup("5"));
        fingerprint.add(makeHitGroup("6"));
        fingerprint.add(makeHitGroup("7"));

        MockResultProvider mockResultProvider = new MockResultProvider(15, true);
        mockResultProvider.addGroupList(fingerprint);
        mockResultProvider.resultGroup.setField(UniqueGroupingSearcher.LABEL_COUNT, 42l);
        Result result = search("?query=foo&unique=fingerprint&hits=5&offset=1", mockResultProvider);
        assertEquals(5, result.hits().size());
        assertEquals("2", result.hits().get(0).getId().toString());
        assertEquals("3", result.hits().get(1).getId().toString());
        assertEquals("4", result.hits().get(2).getId().toString());
        assertEquals("5", result.hits().get(3).getId().toString());
        assertEquals("6", result.hits().get(4).getId().toString());
        assertEquals(42, result.getTotalHitCount());
    }

    @Test
    void testGroupingBasedDedupWithGroupingHitsAndSorting() {
        GroupList fingerprint = new GroupList("fingerprint");
        fingerprint.add(makeSortingHitGroup("1"));
        fingerprint.add(makeSortingHitGroup("2"));
        fingerprint.add(makeSortingHitGroup("3"));
        fingerprint.add(makeSortingHitGroup("4"));
        fingerprint.add(makeSortingHitGroup("5"));
        fingerprint.add(makeSortingHitGroup("6"));
        fingerprint.add(makeSortingHitGroup("7"));

        MockResultProvider mockResultProvider = new MockResultProvider(100, true);
        mockResultProvider.addGroupList(fingerprint);
        mockResultProvider.resultGroup.setField(UniqueGroupingSearcher.LABEL_COUNT, 1337l);

        Result result = search("?query=foo&unique=fingerprint&hits=5&offset=1&sorting=-expdate", mockResultProvider);
        assertEquals(5, result.hits().size());
        assertEquals("2", result.hits().get(0).getId().toString());
        assertEquals("3", result.hits().get(1).getId().toString());
        assertEquals("4", result.hits().get(2).getId().toString());
        assertEquals("5", result.hits().get(3).getId().toString());
        assertEquals("6", result.hits().get(4).getId().toString());
        assertEquals(1337, result.getTotalHitCount());
    }

    @Test
    void testBuildGroupingExpression() {
        assertEquals("all(group(title) max(11) output(count() as(uniqueCount)) each(max(1) each(output(summary())) " +
                "as(uniqueHits)))",
                UniqueGroupingSearcher
                        .buildGroupingExpression("title", 11, null, null)
                        .toString());
        assertEquals("all(group(fingerprint) max(5) output(count() as(uniqueCount)) each(max(1) " +
                "each(output(summary(attributeprefetch))) as(uniqueHits)))",
                UniqueGroupingSearcher
                        .buildGroupingExpression("fingerprint", 5, "attributeprefetch", null)
                        .toString());
        assertEquals("all(group(fingerprint) max(5) order(neg(max(pubdate))) output(count() as(uniqueCount)) each(" +
                "all(group(neg(pubdate)) max(1) order(neg(max(pubdate))) each(each(output(summary())) " +
                "as(uniqueHits)) as(uniqueGroups))))",
                UniqueGroupingSearcher
                        .buildGroupingExpression("fingerprint", 5, null, new Sorting("-pubdate"))
                        .toString());
        assertEquals("all(group(fingerprint) max(5) order(min(pubdate)) output(count() as(uniqueCount)) each(" +
                "all(group(pubdate) max(1) order(min(pubdate)) each(each(output(summary(attributeprefetch))) " +
                "as(uniqueHits)) as(uniqueGroups))))",
                UniqueGroupingSearcher
                        .buildGroupingExpression("fingerprint", 5, "attributeprefetch", new Sorting("+pubdate"))
                        .toString());
    }

    private static Group makeHitGroup(String name) {
        Group ein = new Group(new StringId(name), new Relevance(0));
        HitList hits = new HitList(UniqueGroupingSearcher.LABEL_HITS);
        hits.add(new Hit(name));
        ein.add(hits);
        return ein;
    }

    private static Group makeSortingHitGroup(String name) {
        Hit hit = new Hit(name);

        HitList hits = new HitList(UniqueGroupingSearcher.LABEL_HITS);
        hits.add(hit);

        Group dedupGroup = new Group(new StringId(name), new Relevance(0));
        dedupGroup.add(hits);

        GroupList dedupedHits = new GroupList(UniqueGroupingSearcher.LABEL_GROUPS);
        dedupedHits.add(dedupGroup);

        Group ein = new Group(new StringId(name), new Relevance(0));
        ein.add(dedupedHits);
        return ein;
    }

    private static Result search(String query, MockResultProvider result) {
        return new Execution(new Chain<>(new UniqueGroupingSearcher(), result),
                             Execution.Context.createContextStub()).search(new Query(query));
    }

    private static class MockResultProvider extends Searcher {

        final RootGroup resultGroup;
        final long totalHitCount;
        final boolean addGroupingData;

        MockResultProvider(long totalHitCount, boolean addGroupingData) {
            this.addGroupingData = addGroupingData;
            this.resultGroup = new RootGroup(0, null);
            this.totalHitCount = totalHitCount;
        }

        MockResultProvider addGroupList(GroupList groupList) {
            resultGroup.add(groupList);
            return this;
        }

        @Override
        public Result search(Query query, Execution execution) {
            Result result = new Result(query);
            if (addGroupingData) {
                result.hits().add(resultGroup);
                result.setTotalHitCount(totalHitCount);
            }
            return result;
        }
    }

}
