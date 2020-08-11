// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.10
 */
public class DocumentTypeIdTestCase {

    @Test
    public void requireThatToStringWorks() {
        DocumentTypeId r = new DocumentTypeId(123);
        assertThat(r.toString().contains("123"), is(true));
    }

    @Test
    public void requireThatEqualsAndHashCodeWorks() {
        DocumentTypeId r1 = new DocumentTypeId(123);
        DocumentTypeId r2 = new DocumentTypeId(123);
        DocumentTypeId r3 = new DocumentTypeId(456);

        assertThat(r1, equalTo(r1));
        assertThat(r1, equalTo(r2));
        assertThat(r2, equalTo(r1));
        assertThat(r1.hashCode(), equalTo(r2.hashCode()));

        assertThat(r1, not(equalTo(r3)));
        assertThat(r3, not(equalTo(r1)));
        assertThat(r2, not(equalTo(r3)));
        assertThat(r3, not(equalTo(r2)));
        assertThat(r1.hashCode(), not(equalTo(r3.hashCode())));

        assertThat(r1, not(equalTo(new Object())));
        assertThat(r1.equals("foobar"), is(false));
    }

    @Test
    public void requireThatAccessorsWork() {
        DocumentTypeId r1 = new DocumentTypeId(123);
        assertThat(r1.getId(), equalTo(123));
    }
}
