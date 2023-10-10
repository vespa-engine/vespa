// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;

/**
 * @author Simon Thoresen Hult
 */
public interface FieldTypeAdapter {

    DataType getInputType(Expression exp, String fieldName);

    void tryOutputType(Expression exp, String fieldName, DataType valueType);

}
