// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
public class DocumentRemoveTestCase {

    @Test
    public void requireThatToStringWorks() {
        DocumentId docId = new DocumentId("doc:this:is:a:test");
        DocumentRemove r = new DocumentRemove(docId);
        assertThat(r.toString().contains(docId.toString()), is(true));
    }

    @Test
    public void requireThatEqualsAndHashCodeWorks() {
        DocumentRemove r1 = new DocumentRemove(new DocumentId("doc:this:is:a:test"));
        DocumentRemove r2 = new DocumentRemove(new DocumentId("doc:this:is:a:test"));
        DocumentRemove r3 = new DocumentRemove(new DocumentId("doc:this:is:nonequal"));

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
        assertThat(r1.equals("banana"), is(false));
    }
}
