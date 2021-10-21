// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change.search;

import com.yahoo.searchdefinition.Schema;
import com.yahoo.searchdefinition.document.ImmutableSDField;
import com.yahoo.searchdefinition.document.Matching;
import com.yahoo.searchdefinition.document.NormalizeLevel;
import com.yahoo.searchdefinition.document.Stemming;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryTransform;

/**
 * Class used to build a message describing the usual field changes causing changes in the indexing script.
 * This message should be more descriptive for the end-user than just seeing the changed indexing script.
 *
 * @author geirst
 * @since 2014-12-09
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
        checkMatching(builder);
        checkStemming(builder);
        checkNormalizing(builder);
        checkSummaryTransform(builder);
    }

    private void checkIndexing(ChangeMessageBuilder builder) {
        if (currentField.doesIndexing() != nextField.doesIndexing()) {
            String change = nextField.doesIndexing() ? "add" : "remove";
            builder.addChange(change + " index aspect");
        }
    }

    private void checkMatching(ChangeMessageBuilder builder) {
        Matching currentMatching = currentField.getMatching();
        Matching nextMatching = nextField.getMatching();
        if (!currentMatching.equals(nextMatching)) {
            builder.addChange("matching", toString(currentMatching), toString(nextMatching));
        }
    }

    private void checkStemming(ChangeMessageBuilder builder) {
        Stemming currentStemming = currentField.getStemming(currentSchema);
        Stemming nextStemming = nextField.getStemming(nextSchema);
        if (!currentStemming.equals(nextStemming)) {
            builder.addChange("stemming", currentStemming.getName(), nextStemming.getName());
        }
    }

    private void checkNormalizing(ChangeMessageBuilder builder) {
        NormalizeLevel.Level currentLevel = currentField.getNormalizing().getLevel();
        NormalizeLevel.Level nextLevel = nextField.getNormalizing().getLevel();
        if (!currentLevel.equals(nextLevel)) {
            builder.addChange("normalizing", currentLevel.toString(), nextLevel.toString());
        }
    }

    private void checkSummaryTransform(ChangeMessageBuilder builder) {
        for (SummaryField nextSummaryField : nextField.getSummaryFields().values()) {
            String fieldName = nextSummaryField.getName();
            SummaryField currentSummaryField = currentField.getSummaryField(fieldName);
            if (currentSummaryField != null) {
                SummaryTransform currentTransform = currentSummaryField.getTransform();
                SummaryTransform nextTransform = nextSummaryField.getTransform();
                if (!currentSummaryField.getTransform().equals(nextSummaryField.getTransform())) {
                    builder.addChange("summary field '" + fieldName + "' transform",
                            currentTransform.getName(), nextTransform.getName());
                }
            }
        }
    }

    private static String toString(Matching matching) {
        Matching.Type type = matching.getType();
        String retval = type.getName();
        if (type.equals(Matching.Type.GRAM)) {
            retval += " (size " + matching.getGramSize() + ")";
        }
        return retval;
    }

}
