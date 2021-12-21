// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.objects;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author arnej27959
 */
public class FieldBaseTestCase {

    @Test
    public void testFieldBaseAPI() {
        String s1 = "test";
        FieldBase f1 = new FieldBase(s1);
        FieldBase f2 = new FieldBase("tESt");
        FieldBase f3 = new FieldBase("TEST");

        assertThat(f1.getName(), is(s1));
        assertThat(f1, equalTo(f1));
        assertThat(f1, equalTo(new FieldBase("test")));
        assertThat(f1, equalTo(f2));
        assertThat(f1, equalTo(f3));

        assertThat(f1.hashCode(), equalTo(s1.hashCode()));
        assertThat(f1.hashCode(), equalTo(f2.hashCode()));
        assertThat(f1.hashCode(), equalTo(f3.hashCode()));

        assertThat(f1.toString(), equalTo("field test"));

        FieldBase f4 = new FieldBase("foo");
        FieldBase f5 = new FieldBase("bar");
        FieldBase f6 = new FieldBase("qux");

        assertThat(f1, not(equalTo(f4)));
        assertThat(f1, not(equalTo(f5)));
        assertThat(f1, not(equalTo(f6)));

        assertThat(f1.hashCode(), not(equalTo(f4.hashCode())));
        assertThat(f1.hashCode(), not(equalTo(f5.hashCode())));
        assertThat(f1.hashCode(), not(equalTo(f6.hashCode())));
    }

}
