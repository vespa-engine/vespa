// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;

/**
 * Returns information about the types of fields.
 *
 * @author Simon Thoresen Hult
 */
public interface FieldTypes {

    DataType getFieldType(String fieldName, Expression expression);

}
