// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.Document;
import com.yahoo.vespa.indexinglanguage.FieldValuesFactory;

/**
 * Thrown by {@link Expression#execute(FieldValuesFactory, Document, boolean)}
 * and similar overloads when the input document is invalid.
 *
 * @author bjorncs
 */
public class InvalidInputException extends IllegalArgumentException {

    public InvalidInputException(String message) { super(message); }
}
