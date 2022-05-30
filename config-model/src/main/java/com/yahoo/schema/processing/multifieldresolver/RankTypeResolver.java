// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing.multifieldresolver;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.document.RankType;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.Schema;

import java.util.List;
import java.util.logging.Level;

/**
 * Checks if fields have defined different rank types for the same
 * index (typically in an index-to statement), and if they have
 * output a warning and use the first ranktype.
 *
 * @author hmusum
 */
public class RankTypeResolver extends MultiFieldResolver {

    public RankTypeResolver(String indexName, List<SDField> fields, Schema schema, DeployLogger logger) {
        super(indexName, fields, schema, logger);
    }

    public void resolve() {
        RankType rankType = null;
        if (fields.size() > 0) {
            boolean first = true;
            for (SDField field : fields) {
                if (first) {
                    rankType = fields.get(0).getRankType();
                    first = false;
                } else if (!field.getRankType().equals(rankType)) {
                    deployLogger.logApplicationPackage(Level.WARNING, "In field '" + field.getName() + "' " +
                            field.getRankType() + " for index '" + indexName +
                            "' conflicts with " + rankType +
                            " defined for the same index in field '" +
                            field.getName() + "'. Using " +
                            rankType + ".");
                    field.setRankType(rankType);
                }
            }
        }
    }
}

