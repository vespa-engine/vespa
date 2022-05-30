// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

import com.yahoo.document.DataType;

/**
 * Common interface for various typed key (or field definitions).
 * Used by code which wants to use common algorithms for dealing with typed keys, like the logical mapping
 *
 * @author bratseth
 */
public interface TypedKey {

    String getName();

    void setDataType(DataType type);

    DataType getDataType();

}
