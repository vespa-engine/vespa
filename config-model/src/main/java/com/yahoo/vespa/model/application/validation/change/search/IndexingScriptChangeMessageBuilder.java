// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change.search;

import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.Matching;
import com.yahoo.searchdefinition.document.NormalizeLevel;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.document.Stemming;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryTransform;

/**
 * Class used to build a message describing the usual field changes causing changes in the indexing script.
 * This message should be more descriptive for the end-user than just seeing the changed indexing script.
 *
 * @author  <a href="mailto:geirst@yahoo-inc.com">Geir Storli</a>
 * @since 2014-12-09
 */
public class IndexingScriptChangeMessageBuilder {

    private final Search currentSearch;
    private final SDField currentField;
    private final Search nextSearch;
    private final SDField nextField;

    public IndexingScriptChangeMessageBuilder(Search currentSearch, SDField currentField,
                                              Search nextSearch, SDField nextField) {
        this.currentSearch = currentSearch;
        this.currentField = currentField;
        this.nextSearch = nextSearch;
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
        Stemming currentStemming = currentField.getStemming(currentSearch);
        Stemming nextStemming = nextField.getStemming(nextSearch);
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
        for (SummaryField nextSummaryField : nextField.getSummaryFields()) {
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
