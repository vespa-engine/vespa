// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.Document;
import com.yahoo.vespa.indexinglanguage.expressions.FieldValues;

/**
 * @author Simon Thoresen Hult
 */
public interface DocumentFieldValues extends FieldValues {

    Document getFullOutput();

    Document getUpdatableOutput();

    @Override
    default boolean isComplete() { return true; }

}
