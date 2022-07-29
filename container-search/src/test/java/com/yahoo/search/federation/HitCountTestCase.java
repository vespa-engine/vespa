// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Tony Vaagenes
 */
public class HitCountTestCase {

    @Test
    void require_that_offset_and_hits_are_adjusted_when_federating() {
        final int chain1RelevanceMultiplier = 1;
        final int chain2RelevanceMultiplier = 10;

        FederationTester tester = new FederationTester();
        tester.addSearchChain("chain1", new AddHitsWithRelevanceSearcher("chain1", chain1RelevanceMultiplier));
        tester.addSearchChain("chain2", new AddHitsWithRelevanceSearcher("chain2", chain2RelevanceMultiplier));

        Query query = new Query();
        query.setHits(5);

        query.setOffset(0);
        assertAllHitsFrom("chain2", flattenAndTrim(tester.search(query)));

        query.setOffset(5);
        assertAllHitsFrom("chain1", flattenAndTrim(tester.search(query)));
    }

    @Test
    void require_that_hit_counts_are_merged() {
        final long chain1TotalHitCount = 3;
        final long chain1DeepHitCount = 5;

        final long chain2TotalHitCount = 7;
        final long chain2DeepHitCount = 11;

        FederationTester tester = new FederationTester();
        tester.addSearchChain("chain1", new SetHitCountsSearcher(chain1TotalHitCount, chain1DeepHitCount));
        tester.addSearchChain("chain2", new SetHitCountsSearcher(chain2TotalHitCount, chain2DeepHitCount));

        Result result = tester.searchAndFill();

        assertEquals(result.getTotalHitCount(), chain1TotalHitCount + chain2TotalHitCount);
        assertEquals(result.getDeepHitCount(), chain1DeepHitCount + chain2DeepHitCount);
    }

    @Test
    void require_that_logging_hit_is_populated_with_result_count() {
        final long chain1TotalHitCount = 9;
        final long chain1DeepHitCount = 14;

        final long chain2TotalHitCount = 11;
        final long chain2DeepHitCount = 15;

        FederationTester tester = new FederationTester();
        tester.addSearchChain("chain1",
                new SetHitCountsSearcher(chain1TotalHitCount, chain1DeepHitCount));

        tester.addSearchChain("chain2",
                new SetHitCountsSearcher(chain2TotalHitCount, chain2DeepHitCount),
                new AddHitsWithRelevanceSearcher("chain1", 2));

        Query query = new Query();
        query.setOffset(2);
        query.setHits(7);
        Result result = tester.search();
        List<Hit> metaHits = getFirstMetaHitInEachGroup(result);

        Hit first = metaHits.get(0);
        assertEquals(chain1TotalHitCount, first.getField("count_total"));
        assertEquals(chain1TotalHitCount, first.getField("count_total"));
        assertEquals(1, first.getField("count_first"));
        assertEquals(0, first.getField("count_last"));

        Hit second = metaHits.get(1);
        assertEquals(chain2TotalHitCount, second.getField("count_total"));
        assertEquals(chain2TotalHitCount, second.getField("count_total"));
        assertEquals(1, second.getField("count_first"));
        assertEquals(AddHitsWithRelevanceSearcher.numHitsAdded, second.getField("count_last"));

    }

    private List<Hit> getFirstMetaHitInEachGroup(Result result) {
        List<Hit> metaHits = new ArrayList<>();
        for (Hit topLevelHit : result.hits()) {
            if (topLevelHit instanceof HitGroup) {
                for (Hit hit : (HitGroup)topLevelHit) {
                    if (hit.isMeta()) {
                        metaHits.add(hit);
                        break;
                    }
                }
            }
        }
        return metaHits;
    }

    private void assertAllHitsFrom(String chainName, HitGroup flattenedHits) {
        for (Hit hit : flattenedHits) {
            assertTrue(hit.getId().toString().startsWith(chainName));
        }
    }

    private HitGroup flattenAndTrim(Result result) {
        HitGroup flattenedHits = new HitGroup();
        result.setQuery(result.getQuery());
        flatten(result.hits(), flattenedHits);

        flattenedHits.trim(result.getQuery().getOffset(), result.getQuery().getHits());
        return flattenedHits;
    }

    private void flatten(HitGroup hits, HitGroup flattenedHits) {
        for (Hit hit : hits) {
            if (hit instanceof HitGroup) {
                flatten((HitGroup) hit, flattenedHits);
            } else {
                flattenedHits.add(hit);
            }
        }
    }
}
