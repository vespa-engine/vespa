// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.document.TensorDataType;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.FieldSet;
import com.yahoo.schema.document.ImmutableSDField;
import com.yahoo.schema.document.Matching;
import com.yahoo.schema.document.NormalizeLevel;
import com.yahoo.schema.document.Stemming;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.LinkedList;
import java.util.logging.Level;

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

    private static final String fieldSetDocUrl = "https://docs.vespa.ai/en/reference/schema-reference.html#fieldset";

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
            checkTypes(schema, fieldSet);
        }
    }

    private void checkFieldNames(Schema schema, FieldSet fieldSet) {
        var invalidFieldNames = fieldSet.getFieldNames().stream()
                .filter(f -> schema.getField(f) == null)
                .map(f -> "'" + f  + "'")
                .toList();
        if (invalidFieldNames.isEmpty()) return;

        var message = "For " + schema + ": ";
        if (invalidFieldNames.size() == 1)
            message = message +  "Field " + invalidFieldNames.get(0) + " in " + fieldSet + " does not exist.";
        else
            message = message + "Fields " + String.join(",", invalidFieldNames) + " in " + fieldSet + " do not exist.";
        throw new IllegalArgumentException(message);
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
                    final var buf = new StringBuilder();
                    buf.append("For schema '").append(schema.getName()).append("': ");
                    buf.append("The matching settings in ").append(fieldSet);
                    buf.append(" are inconsistent (explicitly or because of field type). ");
                    buf.append("This may lead to recall and ranking issues. ");
                    Matching original = fieldSet.getMatching();
                    if (original == null) {
                        buf.append("The fieldset will use matching TEXT. ");
                    } else {
                        buf.append("The fieldset will use matching ").append(original.getType()).append(". ");
                    }
                    var list = fieldSet.getFieldNames().stream()
                            .map(name -> schema.getField(name))
                            .filter(f -> (f != null))
                            .filter(f -> (f.getMatching() != null))
                            .map(f -> " Field '" + f.asField().getName() + "' has matching " + f.getMatching().getType())
                            .toList();
                    buf.append(list);
                    buf.append(" See ").append(fieldSetDocUrl);
                    deployLogger.logApplicationPackage(Level.WARNING, buf.toString());
                    // TODO: Remove (see FieldSetSettingsTestCase#inconsistentMatchingShouldStillSetMatchingForFieldSet)
                    // but when doing so matching for a fieldset might change
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
                                    "(explicitly or because of field type). This may lead to recall and ranking issues. " +
                                    "See " + fieldSetDocUrl);
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
                                    "This may lead to recall and ranking issues. " +
                                    "See " + fieldSetDocUrl);
                }
            }
        }
    }

    private void checkTypes(Schema schema, FieldSet fieldSet) {
        var tensorFields = new LinkedList<String>();
        var nonTensorFields = new LinkedList<String>();
        for (String fieldName : fieldSet.getFieldNames()) {
            ImmutableSDField field = schema.getField(fieldName);
            if (field.getDataType() instanceof TensorDataType) {
                tensorFields.add(field.getName());
            } else {
                nonTensorFields.add(field.getName());
            }
        }
        if (!tensorFields.isEmpty() && !nonTensorFields.isEmpty()) {
            var fullMsg = "For schema '" + schema.getName() + "', fieldset '" + fieldSet.getName() + "': " +
                    "Tensor fields ['" + String.join("', '", tensorFields) + "'] " +
                    "cannot be mixed with non-tensor fields ['" + String.join("', '", nonTensorFields) + "'] " +
                    "in the same fieldset. See " + fieldSetDocUrl;
            deployLogger.logApplicationPackage(Level.WARNING, fullMsg);
        }
    }
}
