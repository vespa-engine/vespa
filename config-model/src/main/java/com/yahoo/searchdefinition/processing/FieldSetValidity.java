// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
public class FieldSetValidity extends Processor {

    public FieldSetValidity(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        if ( ! validate) return;

        for (FieldSet fieldSet : search.fieldSets().userFieldSets().values()) {
            checkFieldNames(search, fieldSet);
            checkMatching(search, fieldSet);
            checkNormalization(search, fieldSet);
            checkStemming(search, fieldSet);
        }
    }

    private static void checkFieldNames(Search search, FieldSet fieldSet) {
        for (String fld : fieldSet.getFieldNames()) {
            ImmutableSDField field = search.getField(fld);
            if (field == null) {
                throw new IllegalArgumentException(
                        "For search '" + search.getName() + "': Field '"+ fld + "' in " + fieldSet + " does not exist.");
            }
        }
    }

    private void checkMatching(Search search, FieldSet fieldSet) {
        Matching fsMatching = null;
        for (String fld : fieldSet.getFieldNames()) {
            ImmutableSDField field = search.getField(fld);
            Matching fieldMatching = field.getMatching();
            if (fsMatching==null) {
                fsMatching = fieldMatching;
            } else {
                if ( ! fsMatching.equals(fieldMatching)) {
                    warn(search, field.asField(),
                            "The matching settings for the fields in " + fieldSet + " are inconsistent " +
                                    "(explicitly or because of field type). This may lead to recall and ranking issues.");
                    return;
                }
            }
        }
    }

    private void checkNormalization(Search search, FieldSet fieldSet) {
        NormalizeLevel.Level fsNorm = null;
        for (String fld : fieldSet.getFieldNames()) {
            ImmutableSDField field = search.getField(fld);
            NormalizeLevel.Level fieldNorm = field.getNormalizing().getLevel();
            if (fsNorm==null) {
                fsNorm = fieldNorm;
            } else {
                if ( ! fsNorm.equals(fieldNorm)) {
                    warn(search, field.asField(),
                            "The normalization settings for the fields in " + fieldSet + " are inconsistent " +
                                    "(explicitly or because of field type). This may lead to recall and ranking issues.");
                    return;
                }
            }
        }
    }

    private void checkStemming(Search search, FieldSet fieldSet) {
        Stemming fsStemming = null;
        for (String fld : fieldSet.getFieldNames()) {
            ImmutableSDField field = search.getField(fld);
            Stemming fieldStemming = field.getStemming();
            if (fsStemming==null) {
                fsStemming = fieldStemming;
            } else {
                if ( ! fsStemming.equals(fieldStemming)) {
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
