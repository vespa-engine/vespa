// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class ProcessResultTest {
    @Test
    public void testBasicProperties() {
        ProcessResult processResult = new ProcessResult(0, "foo", "bar");
        assertThat(processResult.getExitStatus(), is(0));
        assertThat(processResult.getOutput(), is("foo"));
        assertThat(processResult.isSuccess(), is(true));
    }

    @Test
    public void testSuccessFails() {
        ProcessResult processResult = new ProcessResult(1, "foo", "bar");
        assertThat(processResult.isSuccess(), is(false));
    }
}
