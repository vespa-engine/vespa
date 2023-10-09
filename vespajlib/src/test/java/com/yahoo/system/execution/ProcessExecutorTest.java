// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.system.execution;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author gjoranv
 */
public class ProcessExecutorTest {

    @Test
    public void echo_can_be_executed() throws Exception {
        final String message = "Hello from executor!";
        ProcessExecutor executor = new ProcessExecutor.Builder(10).build();
        Optional<ProcessResult> result = executor.execute("echo " + message);
        assertTrue(result.isPresent());
        assertEquals(message, result.get().stdOut.trim());
        assertEquals("", result.get().stdErr);
    }
}
