// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import org.junit.Test;
import org.junit.After;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 */
@SuppressWarnings("removal")
public class FileLogTargetTest {
    @Test(expected = RuntimeException.class)
    public void requireThatExceptionIsThrowIfFileNotFound() throws IOException {
        File file = new File("mydir1");
        file.delete();
        assertTrue(file.mkdir());
        new FileLogTarget(file).open();
    }

    @After
    public void cleanup() { new File("mydir1").delete(); }

    @Test
    public void requireThatFileCanOpened() throws IOException {
        FileLogTarget logTarget = new FileLogTarget(File.createTempFile("logfile", ".log"));
        assertNotNull(logTarget.open());
    }

    @Test
    public void requireThatFileIsReopened() throws IOException {
        FileLogTarget logTarget = new FileLogTarget(File.createTempFile("logfile", ".log"));
        OutputStream out1 = logTarget.open();
        assertNotNull(out1);
        OutputStream out2 = logTarget.open();
        assertNotNull(out2);
        assertNotEquals(out1, out2);
    }
}
