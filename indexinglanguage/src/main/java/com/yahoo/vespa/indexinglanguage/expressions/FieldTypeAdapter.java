// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;

/**
 * Returns information about the input and output types of fields.
 *
 * @author Simon Thoresen Hult
 */
public interface FieldTypeAdapter {

    DataType getInputType(Expression expression, String fieldName);

    void tryOutputType(Expression expression, String fieldName, DataType valueType);

}
