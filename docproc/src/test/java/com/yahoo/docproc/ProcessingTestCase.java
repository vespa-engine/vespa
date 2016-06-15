// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc;

import com.yahoo.document.DocumentOperation;
import org.junit.Test;

import java.util.Iterator;
import java.util.Map;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.10
 */
public class ProcessingTestCase {

    @Test
    public void serviceName() {
        assertThat(new Processing().getServiceName(), is("default"));
        assertThat(new Processing("foobar", (DocumentOperation) null, null).getServiceName(), is("foobar"));
    }

    @Test
    public void contextVariables() {
        Processing p = new Processing();

        p.setVariable("foo", "banana");
        p.setVariable("bar", "apple");

        Iterator<Map.Entry<String, Object>> it = p.getVariableAndNameIterator();
        assertThat(it.hasNext(), is(true));
        assertThat(it.next(), not(nullValue()));
        assertThat(it.hasNext(), is(true));
        assertThat(it.next(), not(nullValue()));
        assertThat(it.hasNext(), is(false));

        assertThat(p.hasVariable("foo"), is(true));
        assertThat(p.hasVariable("bar"), is(true));
        assertThat(p.hasVariable("baz"), is(false));

        assertThat(p.removeVariable("foo"), is("banana"));

        p.clearVariables();

        it = p.getVariableAndNameIterator();
        assertThat(it.hasNext(), is(false));
    }
}
