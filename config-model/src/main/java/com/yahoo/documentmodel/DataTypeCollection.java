// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentmodel;

import com.yahoo.document.DataType;

import java.util.Collection;

/**
 * @author baldersheim
 */
public interface DataTypeCollection {

    DataType getDataType(String name);
    DataType getDataType(int id);
    Collection<DataType> getTypes();

}
