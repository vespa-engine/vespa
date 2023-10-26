// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.fieldset;

/**
 * @author Thomas Gundersen
 */
public class DocIdOnly implements FieldSet {
    public static final String NAME = "[id]";
    @Override
    public boolean contains(FieldSet o) {
        return (o instanceof DocIdOnly || o instanceof NoFields);
    }

    @Override
    public FieldSet clone() throws CloneNotSupportedException {
        return new DocIdOnly();
    }
}
