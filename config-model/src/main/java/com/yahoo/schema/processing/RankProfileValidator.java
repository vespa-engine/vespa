// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfile;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Validates rank profile settings.
 *
 * @author bratseth
 */
public class RankProfileValidator extends Processor {

    public RankProfileValidator(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        if (!validate) return;
        if (documentsOnly) return;

        rankProfileRegistry.rankProfilesOf(schema).forEach(profile -> validate(profile));
    }

    private void validate(RankProfile profile) {
        if (profile.getMatchPhase() != null &&
            profile.getMatchPhase().getMaxHits().isPresent() && profile.getMatchPhase().getTotalMaxHits().isPresent())
            throw new IllegalArgumentException("In " + schema + ", " + profile + ": Cannot set or inherit both " +
                                               "match-phase max-hits and total-max-hits");
        if (profile.getRerankCount().isPresent() && profile.getTotalRerankCount().isPresent())
            throw new IllegalArgumentException("In " + schema + ", " + profile + ": Cannot set or inherit both " +
                                               "second-phase rerank-count and total-rerank-count");
        if (profile.getKeepRankCount().isPresent() && profile.getTotalKeepRankCount().isPresent())
            throw new IllegalArgumentException("In " + schema + ", " + profile + ": Cannot set or inherit both " +
                                               "first-phase keep-rank-count and total-keep-rank-count");
    }

}
