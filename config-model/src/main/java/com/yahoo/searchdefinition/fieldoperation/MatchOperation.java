// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.fieldoperation;

import com.yahoo.searchdefinition.document.Matching;
import com.yahoo.searchdefinition.document.SDField;

/**
 * @author Einar M R Rosenvinge
 */
public class MatchOperation implements FieldOperation {

    private Matching.Type matchingType;
    private Integer gramSize;
    private Integer maxLength;
    private Matching.Algorithm matchingAlgorithm;
    private String exactMatchTerminator;

    public void setMatchingType(Matching.Type matchingType) {
        this.matchingType = matchingType;
    }

    public void setGramSize(Integer gramSize) {
        this.gramSize = gramSize;
    }
    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }

    public void setMatchingAlgorithm(Matching.Algorithm matchingAlgorithm) {
        this.matchingAlgorithm = matchingAlgorithm;
    }

    public void setExactMatchTerminator(String exactMatchTerminator) {
        this.exactMatchTerminator = exactMatchTerminator;
    }

    public void apply(SDField field) {
        if (matchingType != null) {
            field.setMatchingType(matchingType);
        }
        if (gramSize != null) {
            field.getMatching().setGramSize(gramSize);
        }
        if (maxLength != null) {
            field.getMatching().maxLength(maxLength);
        }
        if (matchingAlgorithm != null) {
            field.setMatchingAlgorithm(matchingAlgorithm);
        }
        if (exactMatchTerminator != null) {
            field.getMatching().setExactMatchTerminator(exactMatchTerminator);
        }
    }

}
