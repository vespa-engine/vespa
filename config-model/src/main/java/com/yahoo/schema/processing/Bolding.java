// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.document.ImmutableSDField;
import com.yahoo.schema.Schema;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Checks that bolding or dynamic summary is turned on only for text fields. Throws exception if it is turned on for any
 * other fields (otherwise will cause indexing failure)
 *
 * @author hmusum
 */
public class Bolding extends Processor {

    public Bolding(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        if ( ! validate) return;
        for (ImmutableSDField field : schema.allConcreteFields()) {
            for (SummaryField summary : field.getSummaryFields().values()) {
                if (summary.getTransform().isBolded() && !DynamicSummaryTransformUtils.hasSupportedType(summary)) {
                    throw new IllegalArgumentException("'bolding: on' for non-text field " +
                                                       "'" + field.getName() + "'" +
                                                       " (" + summary.getDataType() + ")" +
                                                       " is not allowed");
                } else if (summary.getTransform().isDynamic() && !DynamicSummaryTransformUtils.hasSupportedType(summary)) {
                    throw new IllegalArgumentException("'summary: dynamic' for non-text field " +
                                                       "'" + field.getName() + "'" +
                                                       " (" + summary.getDataType() + ")" +
                                                       " is not allowed");
                }
            }
        }
    }
}
