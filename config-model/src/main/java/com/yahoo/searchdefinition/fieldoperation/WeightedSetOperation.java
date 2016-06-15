// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.fieldoperation;

import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.document.DataType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.document.WeightedSetDataType;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class WeightedSetOperation implements FieldOperation {

    private Boolean createIfNonExistent;
    private Boolean removeIfZero;

    public Boolean getCreateIfNonExistent() {
        return createIfNonExistent;
    }

    public void setCreateIfNonExistent(Boolean createIfNonExistent) {
        this.createIfNonExistent = createIfNonExistent;
    }

    public Boolean getRemoveIfZero() {
        return removeIfZero;
    }

    public void setRemoveIfZero(Boolean removeIfZero) {
        this.removeIfZero = removeIfZero;
    }

    public void apply(SDField field) {
        WeightedSetDataType ctype = (WeightedSetDataType) field.getDataType();

        if (createIfNonExistent != null) {
            field.setDataType(DataType.getWeightedSet(ctype.getNestedType(), createIfNonExistent,
                                                      ctype.removeIfZero()));
        }

        ctype = (WeightedSetDataType) field.getDataType();
        if (removeIfZero != null) {
            field.setDataType(DataType.getWeightedSet(ctype.getNestedType(),
                                                      ctype.createIfNonExistent(), removeIfZero));
        }

        ctype = (WeightedSetDataType) field.getDataType();
        for (Object o : field.getAttributes().values()) {
            Attribute attribute = (Attribute) o;
            attribute.setRemoveIfZero(ctype.removeIfZero());
            attribute.setCreateIfNonExistent(ctype.createIfNonExistent());
        }
    }

    @Override
    public String toString() {
        return "WeightedSetOperation{" +
               "createIfNonExistent=" + createIfNonExistent +
               ", removeIfZero=" + removeIfZero +
               '}';
    }

}
