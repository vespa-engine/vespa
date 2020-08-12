// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProcessResultTest {
    @Test
    public void testBasicProperties() {
        ProcessResult processResult = new ProcessResult(0, "foo", "bar");
        assertEquals(0, processResult.getExitStatus());
        assertEquals("foo", processResult.getOutput());
        assertTrue(processResult.isSuccess());
    }

    @Test
    public void testSuccessFails() {
        ProcessResult processResult = new ProcessResult(1, "foo", "bar");
        assertFalse(processResult.isSuccess());
    }
}
