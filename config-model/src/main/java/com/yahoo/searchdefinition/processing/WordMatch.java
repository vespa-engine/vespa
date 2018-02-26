// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.document.Matching;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.document.Stemming;
import com.yahoo.searchdefinition.Search;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * The implementation of word matching - with word matching the field is assumed to contain a single "word" - some
 * contiguous sequence of word and number characters - but without changing the data at the indexing side (as with text
 * matching) to enforce this. Word matching is thus almost like exact matching on the indexing side (no action taken),
 * and like text matching on the query side. This may be suitable for attributes, where people both expect the data to
 * be left as in the input document, and trivially written queries to work by default. However, this may easily lead to
 * data which cannot be matched at all as the indexing and query side does not agree.
 *
 * @author bratseth
 */
public class WordMatch extends Processor {

    public WordMatch(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    public void process(boolean validate) {
        for (SDField field : search.allConcreteFields()) {
            if ( ! field.getMatching().getType().equals(Matching.Type.WORD)) continue;

            field.setStemming(Stemming.NONE);
            field.getNormalizing().inferLowercase();
            field.addQueryCommand("word");
        }
    }

}
