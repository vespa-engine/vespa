// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.datatypes.StringFieldValue;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Einar M R Rosenvinge
 */
public class TemporaryDataTypeTestCase {

    @Test
    public void requireNulls() {
        TemporaryDataType type = new TemporaryDataType(0, "");
        assertThat(type.createFieldValue(new Object()), nullValue());
        assertThat(type.createFieldValue(), nullValue());
        assertThat(type.getValueClass(), nullValue());
        assertThat(type.isValueCompatible(new StringFieldValue("")), is(false));
    }

}
