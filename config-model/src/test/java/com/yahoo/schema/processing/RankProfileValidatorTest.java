// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.schema.RankProfile;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.derived.TestableDeployLogger;
import com.yahoo.vespa.model.container.search.QueryProfiles;
import com.yahoo.yolean.Exceptions;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author bratseth
 */
public class RankProfileValidatorTest {

    @Test
    public void testMaxHitsValidation() {
        maxHitsValidatorFixture(OptionalLong.empty(),      OptionalLong.empty()).process(true, false);
        maxHitsValidatorFixture(OptionalLong.of(10), OptionalLong.empty()).process(true, false);
        maxHitsValidatorFixture(OptionalLong.empty(),      OptionalLong.of(100)).process(true, false);
        try {
            maxHitsValidatorFixture(OptionalLong.of(10), OptionalLong.of(100)).process(true, false);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("In schema 'test', rank profile 'profile1': Cannot set or inherit both match-phase max-hits and total-max-hits",
                         Exceptions.toMessageString(e));
        }
    }

    @Test
    public void testRerankCountValidation() {
        rerankCountValidatorFixture(OptionalInt.empty(), OptionalInt.empty()).process(true, false);
        rerankCountValidatorFixture(OptionalInt.of(10),  OptionalInt.empty()).process(true, false);
        rerankCountValidatorFixture(OptionalInt.empty(), OptionalInt.of(100)).process(true, false);
        try {
            rerankCountValidatorFixture(OptionalInt.of(10), OptionalInt.of(100)).process(true, false);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("In schema 'test', rank profile 'profile1': Cannot set or inherit both second-phase rerank-count and total-rerank-count",
                         Exceptions.toMessageString(e));
        }
    }

    @Test
    public void testKeepRankCountValidation() {
        keepRankCountValidatorFixture(OptionalInt.empty(), OptionalInt.empty()).process(true, false);
        keepRankCountValidatorFixture(OptionalInt.of(10),  OptionalInt.empty()).process(true, false);
        keepRankCountValidatorFixture(OptionalInt.empty(), OptionalInt.of(100)).process(true, false);
        try {
            keepRankCountValidatorFixture(OptionalInt.of(10), OptionalInt.of(100)).process(true, false);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("In schema 'test', rank profile 'profile1': Cannot set or inherit both first-phase keep-rank-count and total-keep-rank-count",
                         Exceptions.toMessageString(e));
        }
    }

    private RankProfileValidator maxHitsValidatorFixture(OptionalLong maxHits, OptionalLong totalMaxHits) {
        return fixture(rankProfile -> {
            if (maxHits.isPresent() || totalMaxHits.isPresent()) {
                var matchPhase = new RankProfile.MatchPhaseSettings();
                matchPhase.setAttribute("test");
                maxHits.ifPresent(matchPhase::setMaxHits);
                totalMaxHits.ifPresent(matchPhase::setTotalMaxHits);
                rankProfile.setMatchPhase(matchPhase);
            }
            return rankProfile;
        });
    }

    private RankProfileValidator rerankCountValidatorFixture(OptionalInt rerankCount, OptionalInt totalRerankCount) {
        return fixture(rankProfile -> {
            rerankCount.ifPresent(rankProfile::setRerankCount);
            totalRerankCount.ifPresent(rankProfile::setTotalRerankCount);
            return rankProfile;
        });
    }

    private RankProfileValidator keepRankCountValidatorFixture(OptionalInt keepRankCount, OptionalInt totalKeepRankCount) {
        return fixture(rankProfile -> {
            keepRankCount.ifPresent(rankProfile::setKeepRankCount);
            totalKeepRankCount.ifPresent(rankProfile::setTotalKeepRankCount);
            return rankProfile;
        });
    }

    private RankProfileValidator fixture(UnaryOperator<RankProfile> rankProfileModifier) {
        var schema = new Schema("test",
                                MockApplicationPackage.createEmpty(),
                                new MockFileRegistry(),
                                new TestableDeployLogger(),
                                new TestProperties());
        var rankProfiles = new RankProfileRegistry();
        var rankProfile = new RankProfile("profile1", schema, rankProfiles);
        rankProfileModifier.apply(rankProfile);
        rankProfiles.add(rankProfile);
        return new RankProfileValidator(schema, schema.getDeployLogger(), rankProfiles, new QueryProfiles());
    }

}
