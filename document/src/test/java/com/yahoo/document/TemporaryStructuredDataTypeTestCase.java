// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.10
 */
public class TemporaryStructuredDataTypeTestCase {
    @Test
    public void basic() {
        TemporaryStructuredDataType type = TemporaryStructuredDataType.create("banana");
        assertThat(type.getName(), equalTo("banana"));
        int originalId = type.getId();
        type.setName("apple");
        assertThat(type.getName(), equalTo("apple"));
        assertThat(originalId, not(equalTo(type.getId())));
    }
}
