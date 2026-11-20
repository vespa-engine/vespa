// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.ImmutableSDField;
import com.yahoo.schema.document.Ranking;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Validates the 'label-postings' attribute setting against other field settings.
 * 'rank: filter' makes the backend store large posting lists
 * as bitvectors only, without the weights needed by the label postings, so it is rejected.
 */
public class LabelPostingsValidator extends Processor {

    public LabelPostingsValidator(Schema schema,
                                  DeployLogger deployLogger,
                                  RankProfileRegistry rankProfileRegistry,
                                  QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        if (!validate) {
            return;
        }
        for (var field : schema.allConcreteFields()) {
            for (var attribute : field.getAttributes().values()) {
                if (attribute.isLabelPostings() && isRankFilter(field)) {
                    fail(schema, field, "The 'label-postings' attribute setting is not supported together with 'rank: filter'");
                }
            }
        }
    }

    private static boolean isRankFilter(ImmutableSDField field) {
        Ranking ranking = field.getRanking();
        return ranking != null && ranking.isFilter();
    }

}
