// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.fieldoperation;

import com.yahoo.searchdefinition.document.RankType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.Index;

/**
 * @author Einar M R Rosenvinge
 */
public class RankTypeOperation implements FieldOperation {

    private String indexName;
    private RankType type;

    public String getIndexName() {
        return indexName;
    }
    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public RankType getType() {
        return type;
    }
    public void setType(RankType type) {
        this.type = type;
    }

    public void apply(SDField field) {
        if (indexName == null) {
            field.setRankType(type); // Set default if the index is not specified.
        } else {
            Index index = field.getIndex(indexName);
            if (index == null) {
                index = new Index(indexName);
                field.addIndex(index);
            }
            index.setRankType(type);
        }
    }

}
