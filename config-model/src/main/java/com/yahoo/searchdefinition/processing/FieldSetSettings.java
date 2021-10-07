// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.FieldSet;
import com.yahoo.searchdefinition.document.ImmutableSDField;
import com.yahoo.searchdefinition.document.Matching;
import com.yahoo.searchdefinition.document.NormalizeLevel;
import com.yahoo.searchdefinition.document.Stemming;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Computes the right "index commands" for each fieldset in a search definition.
 *
 * @author vegardh
 * @author bratseth
 */
// See also IndexInfo.addFieldSetCommands, which does more of this in a complicated way.
// That should be moved here, and done in the way the match setting is done below
// (this requires adding normalizing and stemming settings to FieldSet).
public class FieldSetSettings extends Processor {

    public FieldSetSettings(Search search,
                            DeployLogger deployLogger,
                            RankProfileRegistry rankProfileRegistry,
                            QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        for (FieldSet fieldSet : search.fieldSets().userFieldSets().values()) {
            if (validate)
                checkFieldNames(search, fieldSet);
            checkMatching(search, fieldSet);
            checkNormalization(search, fieldSet);
            checkStemming(search, fieldSet);
        }
    }

    private void checkFieldNames(Search search, FieldSet fieldSet) {
        for (String field : fieldSet.getFieldNames()) {
            if (search.getField(field) == null)
                throw new IllegalArgumentException("For search '" + search.getName() +
                                                   "': Field '"+ field + "' in " + fieldSet + " does not exist.");
        }
    }

    private void checkMatching(Search search, FieldSet fieldSet) {
        Matching matching = fieldSet.getMatching();
        for (String fieldName : fieldSet.getFieldNames()) {
            ImmutableSDField field = search.getField(fieldName);
            Matching fieldMatching = field.getMatching();
            if (matching == null) {
                matching = fieldMatching;
            } else {
                if ( ! matching.equals(fieldMatching)) {
                    warn(search, field.asField(),
                            "The matching settings for the fields in " + fieldSet + " are inconsistent " +
                                    "(explicitly or because of field type). This may lead to recall and ranking issues.");
                    return;
                }
            }
        }
        fieldSet.setMatching(matching); // Assign the uniquely determined matching to the field set
    }

    private void checkNormalization(Search search, FieldSet fieldSet) {
        NormalizeLevel.Level normalizing = null;
        for (String fieldName : fieldSet.getFieldNames()) {
            ImmutableSDField field = search.getField(fieldName);
            NormalizeLevel.Level fieldNorm = field.getNormalizing().getLevel();
            if (normalizing == null) {
                normalizing = fieldNorm;
            } else {
                if ( ! normalizing.equals(fieldNorm)) {
                    warn(search, field.asField(),
                            "The normalization settings for the fields in " + fieldSet + " are inconsistent " +
                                    "(explicitly or because of field type). This may lead to recall and ranking issues.");
                    return;
                }
            }
        }
    }

    private void checkStemming(Search search, FieldSet fieldSet) {
        Stemming stemming = null;
        for (String fieldName : fieldSet.getFieldNames()) {
            ImmutableSDField field = search.getField(fieldName);
            Stemming fieldStemming = field.getStemming();
            if (stemming == null) {
                stemming = fieldStemming;
            } else {
                if ( ! stemming.equals(fieldStemming)) {
                    warn(search, field.asField(),
                            "The stemming settings for the fields in the fieldset '"+fieldSet.getName()+
                                    "' are inconsistent (explicitly or because of field type). " +
                                    "This may lead to recall and ranking issues.");
                    return;
                }
            }
        }
    }

}
