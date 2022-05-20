// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.FieldSet;
import com.yahoo.schema.document.ImmutableSDField;
import com.yahoo.schema.document.Matching;
import com.yahoo.schema.document.NormalizeLevel;
import com.yahoo.schema.document.Stemming;
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

    public FieldSetSettings(Schema schema,
                            DeployLogger deployLogger,
                            RankProfileRegistry rankProfileRegistry,
                            QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        for (FieldSet fieldSet : schema.fieldSets().userFieldSets().values()) {
            if (validate)
                checkFieldNames(schema, fieldSet);
            checkMatching(schema, fieldSet);
            checkNormalization(schema, fieldSet);
            checkStemming(schema, fieldSet);
        }
    }

    private void checkFieldNames(Schema schema, FieldSet fieldSet) {
        for (String field : fieldSet.getFieldNames()) {
            if (schema.getField(field) == null)
                throw new IllegalArgumentException("For " + schema + ": Field '" + field + "' in " +
                                                   fieldSet + " does not exist.");
        }
    }

    private void checkMatching(Schema schema, FieldSet fieldSet) {
        Matching matching = fieldSet.getMatching();
        for (String fieldName : fieldSet.getFieldNames()) {
            ImmutableSDField field = schema.getField(fieldName);
            Matching fieldMatching = field.getMatching();
            if (matching == null) {
                matching = fieldMatching;
            } else {
                if ( ! matching.equals(fieldMatching)) {
                    warn(schema, field.asField(),
                            "The matching settings for the fields in " + fieldSet + " are inconsistent " +
                                    "(explicitly or because of field type). This may lead to recall and ranking issues.");
                    return;
                }
            }
        }
        fieldSet.setMatching(matching); // Assign the uniquely determined matching to the field set
    }

    private void checkNormalization(Schema schema, FieldSet fieldSet) {
        NormalizeLevel.Level normalizing = null;
        for (String fieldName : fieldSet.getFieldNames()) {
            ImmutableSDField field = schema.getField(fieldName);
            NormalizeLevel.Level fieldNorm = field.getNormalizing().getLevel();
            if (normalizing == null) {
                normalizing = fieldNorm;
            } else {
                if ( ! normalizing.equals(fieldNorm)) {
                    warn(schema, field.asField(),
                            "The normalization settings for the fields in " + fieldSet + " are inconsistent " +
                                    "(explicitly or because of field type). This may lead to recall and ranking issues.");
                    return;
                }
            }
        }
    }

    private void checkStemming(Schema schema, FieldSet fieldSet) {
        Stemming stemming = null;
        for (String fieldName : fieldSet.getFieldNames()) {
            ImmutableSDField field = schema.getField(fieldName);
            Stemming fieldStemming = field.getStemming();
            if (stemming == null) {
                stemming = fieldStemming;
            } else {
                if ( ! stemming.equals(fieldStemming)) {
                    warn(schema, field.asField(),
                            "The stemming settings for the fields in the fieldset '"+fieldSet.getName()+
                                    "' are inconsistent (explicitly or because of field type). " +
                                    "This may lead to recall and ranking issues.");
                    return;
                }
            }
        }
    }

}
