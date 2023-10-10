// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import com.yahoo.document.DataType;

public abstract class CompositeFieldValue extends FieldValue {

    private DataType dataType;

    public CompositeFieldValue(DataType dataType) {
        this.dataType = dataType;
    }

    @Override
    public DataType getDataType() {
        return dataType;
    }

    public void setDataType(DataType dataType) {
        this.dataType = dataType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CompositeFieldValue)) return false;
        if (!super.equals(o)) return false;

        CompositeFieldValue that = (CompositeFieldValue) o;
        if (dataType != null ? !dataType.equals(that.dataType) : that.dataType != null) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (dataType != null ? dataType.hashCode() : 0);
        return result;
    }

}
