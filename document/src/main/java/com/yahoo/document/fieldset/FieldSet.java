// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.fieldset;

import com.yahoo.document.Document;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.FieldValue;

import java.lang.Object;
import java.util.Iterator;
import java.util.Map;

/**
 * TODO: Move to Java and implement.
 */
public interface FieldSet {
    public boolean contains(FieldSet o);

    public FieldSet clone() throws CloneNotSupportedException;
}
