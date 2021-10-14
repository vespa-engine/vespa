// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.searchdefinition.fieldoperation;

import com.yahoo.searchdefinition.document.Case;
import com.yahoo.searchdefinition.document.Dictionary;
import com.yahoo.searchdefinition.document.SDField;

/**
 * Represents operations controlling setup of dictionary used for queries
 *
 * @author baldersheim
 */
public class DictionaryOperation implements FieldOperation {
    public enum Operation { HASH, BTREE, CASED, UNCASED }
    private final Operation operation;

    public DictionaryOperation(Operation type) {
        this.operation = type;
    }
    @Override
    public void apply(SDField field) {
        Dictionary dictionary = field.getOrSetDictionary();
        switch (operation) {
            case HASH:
                dictionary.updateType(Dictionary.Type.HASH);
                break;
            case BTREE:
                dictionary.updateType(Dictionary.Type.BTREE);
                break;
            case CASED:
                dictionary.updateMatch(Case.CASED);
                break;
            case UNCASED:
                dictionary.updateMatch(Case.UNCASED);
                break;
            default:
                throw new IllegalArgumentException("Unhandled operation " + operation);
        }
    }
}
