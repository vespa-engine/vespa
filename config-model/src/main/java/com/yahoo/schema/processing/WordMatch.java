// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.document.MatchType;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.document.Stemming;
import com.yahoo.schema.Schema;
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

    public WordMatch(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    public void process(boolean validate, boolean documentsOnly) {
        for (SDField field : schema.allConcreteFields()) {
            processFieldRecursive(field);
        }
    }

    private void processFieldRecursive(SDField field) {
        processField(field);
        for (SDField structField : field.getStructFields()) {
            processField(structField);
        }
    }

    private void processField(SDField field) {
        if (!field.getMatching().getType().equals(MatchType.WORD)) {
            return;
        }
        field.setStemming(Stemming.NONE);
        field.getNormalizing().inferLowercase();
        field.addQueryCommand("word");
    }


}
