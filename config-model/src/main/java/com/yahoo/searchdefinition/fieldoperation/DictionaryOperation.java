// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.searchdefinition.fieldoperation;

import com.yahoo.searchdefinition.document.Dictionary;
import com.yahoo.searchdefinition.document.SDField;

/**
 * Represents operations controlling setup of dictionary used for queries
 *
 * @author baldersheim
 */
public class DictionaryOperation implements FieldOperation {
    private final Dictionary.Type type;

    public DictionaryOperation(Dictionary.Type type) {
        this.type = type;
    }
    @Override
    public void apply(SDField field) {
        Dictionary prev = field.getDictionary();
        if (prev == null) {
            field.setDictionary(new Dictionary(type));
        } else if ((prev.getType() == Dictionary.Type.BTREE && type == Dictionary.Type.HASH) ||
                   (prev.getType() == Dictionary.Type.HASH && type == Dictionary.Type.BTREE))
        {
            field.setDictionary(new Dictionary(Dictionary.Type.BTREE_AND_HASH));
        } else {
            if (prev.getType() != type) {
                throw new IllegalArgumentException("Can not combine previous dictionary setting " + prev.getType() +
                        " with current " + type);
            }
        }
    }
}
