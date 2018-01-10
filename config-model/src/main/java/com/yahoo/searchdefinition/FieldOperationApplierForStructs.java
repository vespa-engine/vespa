// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Einar M R Rosenvinge
 */
public class FieldOperationApplierForStructs extends FieldOperationApplier {

    @Override
    public void process(SDDocumentType sdoc) {
        for (SDDocumentType type : sdoc.getAllTypes()) {
            if (type.isStruct()) {
                apply(type);
                copyFields(type, sdoc);
            }
        }
    }

    private void copyFields(SDDocumentType structType, SDDocumentType sdoc) {
        //find all fields in OTHER types that have this type:
        List<SDDocumentType> list = new ArrayList<>();
        list.add(sdoc);
        list.addAll(sdoc.getTypes());
        for (SDDocumentType anyType : list) {
            Iterator<Field> fields = anyType.fieldIterator();
            while (fields.hasNext()) {
                SDField field = (SDField) fields.next();
                DataType structUsedByField = field.getFirstStructRecursive();
                if (structUsedByField == null) {
                    continue;
                }
                if (structUsedByField.getName().equals(structType.getName())) {
                    //this field is using this type!!
                    field.populateWithStructFields(sdoc, field.getName(), field.getDataType(), field.isHeader(), 0);
                    field.populateWithStructMatching(sdoc, field.getName(), field.getDataType(), field.getMatching());
                }
            }
        }
    }

}
