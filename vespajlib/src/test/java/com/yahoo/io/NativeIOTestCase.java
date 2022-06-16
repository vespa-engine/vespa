// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io;

import com.sun.jna.Platform;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class NativeIOTestCase {

    @Test
    public void requireThatDropFileFromCacheDoesNotThrow() throws IOException {
        File testFile = new File("testfile");
        FileOutputStream output = new FileOutputStream(testFile);
        output.write('t');
        output.flush();
        output.close();
        NativeIO nativeIO = new NativeIO();
        if (Platform.isLinux()) {
            assertTrue(nativeIO.valid());
        } else {
            assertFalse(nativeIO.valid());
            assertEquals("Platform is unsupported. Only supported on Linux.", nativeIO.getError().getMessage());
        }
        nativeIO.dropFileFromCache(output.getFD());
        nativeIO.dropFileFromCache(testFile);
        testFile.delete();
        nativeIO.dropFileFromCache(new File("file.that.does.not.exist"));
    }
}
