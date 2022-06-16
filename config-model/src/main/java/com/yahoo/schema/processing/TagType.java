// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.document.DataType;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.Matching;
import com.yahoo.schema.document.MatchType;
import com.yahoo.schema.document.RankType;
import com.yahoo.schema.document.SDField;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * The implementation of the tag datatype
 *
 * @author bratseth
 */
public class TagType extends Processor {

    public TagType(Schema schema,
                   DeployLogger deployLogger,
                   RankProfileRegistry rankProfileRegistry,
                   QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        for (SDField field : schema.allConcreteFields()) {
            if (field.getDataType() instanceof WeightedSetDataType && ((WeightedSetDataType)field.getDataType()).isTag())
                implementTagType(field);
        }
    }

    private void implementTagType(SDField field) {
        field.setDataType(DataType.getWeightedSet(DataType.STRING, true, true));
        // Don't set matching and ranking if this field is not attribute nor index
        if (!field.doesIndexing() && !field.doesAttributing()) return;
        Matching m = field.getMatching();
        if ( ! m.isTypeUserSet())
            m.setType(MatchType.WORD);
        if (field.getRankType() == null || field.getRankType() == RankType.DEFAULT)
            field.setRankType((RankType.TAGS));
    }

}
