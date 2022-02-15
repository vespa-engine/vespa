// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.fieldset;

/**
 * @author arnej27959
 */
public class DocumentOnly implements FieldSet {
    public static final String NAME = "[document]";
    @Override
    public boolean contains(FieldSet o) {
        return (o instanceof DocumentOnly || o instanceof DocIdOnly || o instanceof NoFields);
    }

    @Override
    public FieldSet clone() {
        return new DocumentOnly();
    }
}
