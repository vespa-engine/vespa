// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.document.DataType;
import com.yahoo.document.TensorDataType;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.FieldSet;
import com.yahoo.schema.document.ImmutableSDField;
import com.yahoo.schema.document.MatchType;
import com.yahoo.schema.document.Matching;
import com.yahoo.schema.document.NormalizeLevel;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.document.Stemming;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
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

    private static final String fieldSetDocUrl = "https://docs.vespa.ai/en/reference/schemas/schemas.html#fieldset";

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
            checkLinguisticsTokens(schema, fieldSet);
            checkStemming(schema, fieldSet);
            checkUnstemmedTextMix(schema, fieldSet);
            checkTypes(schema, fieldSet);
            assignLinguistics(schema, fieldSet);
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
                    var buf = new StringBuilder();
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
            if ( ! field.isStemmable()) continue; // stemming does not apply to this field
            Stemming fieldStemming = field.getEffectiveSearchStemming(schema);
            if (stemming == null) {
                stemming = fieldStemming;
            } else {
                if ( ! stemming.equals(fieldStemming)) {
                    warn(schema, field.asField(),
                         "The stemming settings for the fields in the fieldset '" + fieldSet.getName()+
                         "' are inconsistent. " +
                         "This may lead to recall and ranking issues. " +
                         "See " + fieldSetDocUrl);
                }
            }
        }
    }

    /**
     * The query terms of a fieldset are stemmed if any of its fields is stemmed, but a field which
     * holds text that is not stemmed - a uri field, or an imported one, which is an attribute - will
     * then not match those terms. Warn about that mix, which is silent otherwise: unlike a word,
     * exact or gram matched field, such a field is text matched, so checkMatching sees nothing wrong
     * with it. Fields which are not stemmed because of their matching are left to checkMatching, and
     * fields which hold no text at all are not warned about: stemming a query term does not change
     * whether it matches the content of, say, an int field.
     */
    private void checkUnstemmedTextMix(Schema schema, FieldSet fieldSet) {
        if ( ! isStemmed(schema, fieldSet)) return;
        for (String fieldName : fieldSet.getFieldNames()) {
            ImmutableSDField field = schema.getField(fieldName);
            if ( ! holdsUnstemmedText(field)) continue;
            warn(schema, field.asField(),
                 "The fields in " + fieldSet + " are stemmed when searched, but the content of this " +
                 "field is not stemmed, so it will not match a stemmed term. " +
                 "This may lead to recall and ranking issues. " +
                 "See " + fieldSetDocUrl);
        }
    }

    /** Returns whether the query terms of this fieldset are stemmed, as IndexInfo decides it. */
    private static boolean isStemmed(Schema schema, FieldSet fieldSet) {
        boolean anyStemmed = false;
        for (String fieldName : fieldSet.getFieldNames()) {
            ImmutableSDField field = schema.getField(fieldName);
            // A word or exact matched field makes the whole fieldset match that way, and then
            // IndexInfo emits no stem command for it at all
            MatchType matching = field.getMatching().getType();
            if (matching == MatchType.WORD || matching == MatchType.EXACT) return false;
            if (field.isStemmable() && field.getEffectiveSearchStemming(schema) != Stemming.NONE)
                anyStemmed = true;
        }
        return anyStemmed;
    }

    /** Returns whether this field holds text which is tokenized, but never stemmed. */
    private static boolean holdsUnstemmedText(ImmutableSDField field) {
        if (field.isStemmable()) return false; // stemmed, or inconsistent in a way checkStemming reports
        if (field.getMatching().getType() != MatchType.TEXT) return false; // checkMatching reports these
        return field.isOfTypeOrNested(DataType.STRING) || field.isOfTypeOrNested(DataType.URI);
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
            throw new IllegalArgumentException(forFieldSet(schema, fieldSet) +
                                               "Illegal mixing of tensor fields ['" + String.join("','", tensorFields) + "'] " +
                                               "and non-tensor fields ['" + String.join("','", nonTensorFields) + "']");
        }
    }

    private void assignLinguistics(Schema schema, FieldSet fieldSet) {
        if (fieldSet.getFieldNames().size() < 2) return;
        Iterator<String> fieldNames = fieldSet.getFieldNames().iterator();
        var firstField = schema.getField(fieldNames.next());
        // Will only be used when all fields share the same profile, see LinguisticsParser
        fieldSet.setLinguisticsProfile(firstField.getSearchLinguisticsProfile());
    }

    /**
     * The fields of a fieldset are searched as one index, so they must agree on which tokens the
     * query terms are turned into. This is an error when a 'tokens' setting is in play, and just a
     * warning (see checkStemming) when it follows from plain stemming settings.
     * <p>
     * Both compare what the query side will actually do, as resolved by
     * {@link ImmutableSDField#getEffectiveSearchStemming}, which is also what IndexInfo emits:
     * an index level stemming setting must count here exactly as it counts there.
     * <p>
     * Every field is compared with every field before it, not just with the first one: comparing
     * with the first only makes the outcome depend on the order of the fields of the fieldset.
     */
    private void checkLinguisticsTokens(Schema schema, FieldSet fieldSet) {
        List<ImmutableSDField> checked = new ArrayList<>();
        for (String fieldName : fieldSet.getFieldNames()) {
            ImmutableSDField field = schema.getField(fieldName);
            if ( ! field.isStemmable()) continue; // tokens does not apply to this field
            Stemming fieldStemming = field.getEffectiveSearchStemming(schema);
            for (ImmutableSDField earlier : checked) {
                // Inconsistency which follows from plain stemming settings alone is only warned about
                if (earlier.getSearchLinguisticsTokens() == null && field.getSearchLinguisticsTokens() == null) continue;
                Stemming earlierStemming = earlier.getEffectiveSearchStemming(schema);
                if ( ! earlierStemming.equals(fieldStemming))
                    throw new IllegalArgumentException(forFieldSet(schema, fieldSet) +
                                                       "Illegal mixing of linguistics search tokens/stemming settings: " +
                                                       earlier + " resolves to '" + earlierStemming + "'" +
                                                       ", while " + field + " resolves to '" + fieldStemming + "'");
            }
            checked.add(field);
        }
    }

    private String forFieldSet(Schema schema, FieldSet fieldSet) {
        return "For " + schema + ", " + fieldSet + ": ";
    }

}
