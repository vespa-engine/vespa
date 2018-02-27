// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.document.*;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.Matching;
import com.yahoo.searchdefinition.document.RankType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * The implementation of the tag datatype
 *
 * @author bratseth
 */
public class TagType extends Processor {

    public TagType(Search search,
                   DeployLogger deployLogger,
                   RankProfileRegistry rankProfileRegistry,
                   QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        for (SDField field : search.allConcreteFields()) {
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
            m.setType(Matching.Type.WORD);
        if (field.getRankType() == null || field.getRankType() == RankType.DEFAULT)
            field.setRankType((RankType.TAGS));
    }

}
