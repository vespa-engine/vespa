// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Processor which validates that a field <code>foo</code> cannot be declared as both
 * <pre>
 *   rank: filter
 * </pre>
 * and
 * <pre>
 *   rank foo {
 *       filter-threshold: ...
 *   }
 * </pre>
 * as part of the document field declaration + rank profile (or both within
 * the same rank profile, if the document schema does not specify `rank: filter`
 * but the rank profile specifies `rank foo: filter`).
 *
 * @author vekterli
 */
public class ValidateNoFieldRankFilterOverlap extends Processor {

    public ValidateNoFieldRankFilterOverlap(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        if (!validate) {
            return;
        }
        var rankProfiles = rankProfileRegistry.rankProfilesOf(schema);
        for (var rp : rankProfiles) {
            var rpFilterFields = rp.allFilterFields();
            for (var field : schema.allConcreteFields()) {
                boolean isFilter = rpFilterFields.contains(field.getName());
                boolean hasExplicitFilterThreshold = rp.explicitFieldRankFilterThresholds().containsKey(field.getName());
                if (isFilter && hasExplicitFilterThreshold) {
                    throw newProcessException(schema.getName(), field.getName(),
                            ("rank profile '%s' declares field as `rank %s { filter-threshold:... }`, but field " +
                             "is also declared as `rank: filter`. These declarations are mutually exclusive.")
                             .formatted(rp.name(), field.getName()));
                }
            }
        }
    }

}
