// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.fieldset;

/**
 * TODO: Move to Java and implement.
 */
public interface FieldSet {
    boolean contains(FieldSet o);

    FieldSet clone() throws CloneNotSupportedException;
}
