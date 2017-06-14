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
public class FieldPathEntryTestCase {
    @Test
    public void testKeyParseResult() {
        FieldPathEntry.KeyParseResult result1 = new FieldPathEntry.KeyParseResult("banana", 2);
        FieldPathEntry.KeyParseResult result2 = new FieldPathEntry.KeyParseResult("banana", 2);
        FieldPathEntry.KeyParseResult result3 = new FieldPathEntry.KeyParseResult("apple", 2);
        FieldPathEntry.KeyParseResult result4 = new FieldPathEntry.KeyParseResult("banana", 3);


        assertThat(result1, equalTo(result2));
        assertThat(result2, equalTo(result1));
        assertThat(result1.hashCode(), equalTo(result2.hashCode()));
        assertThat(result1.toString(), equalTo(result2.toString()));

        assertThat(result1, not(equalTo(result3)));
        assertThat(result3, not(equalTo(result1)));
        assertThat(result1.hashCode(), not(equalTo(result3.hashCode())));
        assertThat(result1.toString(), not(equalTo(result3.toString())));

        assertThat(result1, not(equalTo(result4)));
        assertThat(result4, not(equalTo(result1)));
        assertThat(result1.hashCode(), not(equalTo(result4.hashCode())));
        assertThat(result1.toString(), not(equalTo(result4.toString())));
    }
}
