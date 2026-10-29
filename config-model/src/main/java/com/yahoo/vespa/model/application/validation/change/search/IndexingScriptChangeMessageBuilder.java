// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change.search;

import com.yahoo.schema.Schema;
import com.yahoo.schema.document.ComplexIndexFieldUtils;
import com.yahoo.schema.document.ImmutableSDField;
import com.yahoo.schema.document.Matching;
import com.yahoo.schema.document.MatchType;
import com.yahoo.schema.document.NormalizeLevel;
import com.yahoo.schema.document.Stemming;
import com.yahoo.schema.processing.NGramMatch;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryTransform;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Class used to build a message describing the usual field changes causing changes in the indexing script.
 * This message should be more descriptive for the end-user than just seeing the changed indexing script.
 *
 * @author geirst
 */
public class IndexingScriptChangeMessageBuilder {

    private final Schema currentSchema;
    private final ImmutableSDField currentField;
    private final Schema nextSchema;
    private final ImmutableSDField nextField;

    public IndexingScriptChangeMessageBuilder(Schema currentSchema, ImmutableSDField currentField,
                                              Schema nextSchema, ImmutableSDField nextField) {
        this.currentSchema = currentSchema;
        this.currentField = currentField;
        this.nextSchema = nextSchema;
        this.nextField = nextField;
    }

    public void populate(ChangeMessageBuilder builder) {
        checkIndexing(builder);
        checkMatching(builder, "", currentField, nextField);
        checkStemming(builder, "", currentField, nextField);
        checkNormalizing(builder, "", currentField, nextField);
        checkSummaryTransform(builder);
    }

    /** Describes changes to the struct field indexes of the field, which are indexed by a script derived for the field. */
    public void populateStructFieldIndexes(ChangeMessageBuilder builder) {
        Map<String, ImmutableSDField> currentStructFields = supportedIndexedStructFields(currentField);
        Map<String, ImmutableSDField> nextStructFields = supportedIndexedStructFields(nextField);
        for (ImmutableSDField currentStructField : currentStructFields.values()) {
            if ( ! nextStructFields.containsKey(currentStructField.getName()))
                builder.addChange("remove index aspect for struct field '" + currentStructField.getName() + "'");
        }
        for (ImmutableSDField nextStructField : nextStructFields.values()) {
            String prefix = "struct field '" + nextStructField.getName() + "' ";
            ImmutableSDField currentStructField = currentStructFields.get(nextStructField.getName());
            if (currentStructField == null) {
                builder.addChange("add index aspect for " + prefix.trim());
                continue;
            }
            checkMatching(builder, prefix, currentStructField, nextStructField);
            checkStemming(builder, prefix, currentStructField, nextStructField);
            checkNormalizing(builder, prefix, currentStructField, nextStructField);
        }
    }

    private static Map<String, ImmutableSDField> supportedIndexedStructFields(ImmutableSDField field) {
        Map<String, ImmutableSDField> result = new LinkedHashMap<>();
        for (ImmutableSDField structField : ComplexIndexFieldUtils.supportedIndexedStructFields(field))
            result.put(structField.getName(), structField);
        return result;
    }

    private void checkIndexing(ChangeMessageBuilder builder) {
        if (currentField.doesIndexing() != nextField.doesIndexing()) {
            String change = nextField.doesIndexing() ? "add" : "remove";
            builder.addChange(change + " index aspect");
        }
    }

    private static void checkMatching(ChangeMessageBuilder builder, String prefix,
                                      ImmutableSDField currentField, ImmutableSDField nextField) {
        Matching currentMatching = currentField.getMatching();
        Matching nextMatching = nextField.getMatching();
        if (!currentMatching.equals(nextMatching)) {
            builder.addChange(prefix + "matching", toString(currentMatching), toString(nextMatching));
        }
    }

    private void checkStemming(ChangeMessageBuilder builder, String prefix,
                               ImmutableSDField currentField, ImmutableSDField nextField) {
        // This explains a change to an indexing script, so it is the index side which matters
        Stemming currentStemming = currentField.getIndexStemming(currentSchema);
        Stemming nextStemming = nextField.getIndexStemming(nextSchema);
        if (currentStemming != nextStemming) {
            builder.addChange(prefix + "stemming", currentStemming.getName(), nextStemming.getName());
        }
    }

    private static void checkNormalizing(ChangeMessageBuilder builder, String prefix,
                                         ImmutableSDField currentField, ImmutableSDField nextField) {
        NormalizeLevel.Level currentLevel = currentField.getNormalizing().getLevel();
        NormalizeLevel.Level nextLevel = nextField.getNormalizing().getLevel();
        if (currentLevel != nextLevel) {
            builder.addChange(prefix + "normalizing", currentLevel.toString(), nextLevel.toString());
        }
    }

    private void checkSummaryTransform(ChangeMessageBuilder builder) {
        for (SummaryField nextSummaryField : nextField.getSummaryFields().values()) {
            String fieldName = nextSummaryField.getName();
            SummaryField currentSummaryField = currentField.getSummaryField(fieldName);
            if (currentSummaryField != null) {
                SummaryTransform currentTransform = currentSummaryField.getTransform();
                SummaryTransform nextTransform = nextSummaryField.getTransform();
                if (currentSummaryField.getTransform() != nextSummaryField.getTransform()) {
                    builder.addChange("summary field '" + fieldName + "' transform",
                            currentTransform.getName(), nextTransform.getName());
                }
            }
        }
    }

    private static String toString(Matching matching) {
        MatchType type = matching.getType();
        String retval = type.getName();
        if (type == MatchType.GRAM) {
            retval += " (size " + matching.getGramSize().orElse(NGramMatch.DEFAULT_GRAM_SIZE) + ")";
        }
        return retval;
    }

}
