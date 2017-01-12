// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentmodel;

import com.yahoo.document.DataType;

import java.util.Collection;

/**
 * @author baldersheim
 */
public interface DataTypeCollection {
    public DataType getDataType(String name);
    public DataType getDataType(int id);
    public Collection<DataType> getTypes();
}
