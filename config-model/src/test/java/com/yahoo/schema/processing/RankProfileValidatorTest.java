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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author bratseth
 */
public class RankProfileValidatorTest {

    @Test
    public void testRerankCountValidation() {
        validatorFixture(OptionalInt.empty(),      OptionalInt.empty()).process(true, false);
        validatorFixture(OptionalInt.of(10), OptionalInt.empty()).process(true, false);
        validatorFixture(OptionalInt.empty(),      OptionalInt.of(100)).process(true, false);
        try {
            validatorFixture(OptionalInt.of(10), OptionalInt.of(100)).process(true, false);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("In schema 'test', rank profile 'profile1': Cannot set or inherit both rerank-count and total-rerank-count",
                         Exceptions.toMessageString(e));
        }
    }

    private RankProfileValidator validatorFixture(OptionalInt rerankCount, OptionalInt totalRerankCount) {
        var schema = new Schema("test",
                                MockApplicationPackage.createEmpty(),
                                new MockFileRegistry(),
                                new TestableDeployLogger(),
                                new TestProperties());
        var rankProfiles = new RankProfileRegistry();
        var rankProfile = new RankProfile("profile1", schema, rankProfiles);
        rerankCount.ifPresent(rankProfile::setRerankCount);
        totalRerankCount.ifPresent(rankProfile::setTotalRerankCount);
        rankProfiles.add(rankProfile);
        return new RankProfileValidator(schema, schema.getDeployLogger(), rankProfiles, new QueryProfiles());
    }

}
