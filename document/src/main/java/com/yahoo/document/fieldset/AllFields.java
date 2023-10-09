// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.fieldset;

/**
 * @author Thomas Gundersen
 */
public class AllFields implements FieldSet {
    public static final String NAME = "[all]";
    @Override
    public boolean contains(FieldSet o) {
        return true;
    }

    @Override
    public FieldSet clone() {
        return new AllFields();
    }
}
