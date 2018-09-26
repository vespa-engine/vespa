package com.yahoo.io;

import com.sun.jna.Platform;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


public class NativeIOTestCase {

    @Test
    public void requireThatDropFileFromCacheDoesNotThrow() throws IOException {
        File testFile = new File("testfile");
        FileOutputStream output = new FileOutputStream(testFile);
        output.write('t');
        output.flush();
        output.close();
        try {
            NativeIO nativeIO = new NativeIO();
            nativeIO.dropFileFromCache(output.getFD());
            nativeIO.dropFileFromCache(testFile);
        } catch (Throwable e) {
            if (Platform.isLinux()) {
                assertTrue(false);
            } else {
                assertEquals("Platform is unsúpported. Only supported on linux.", e.getMessage());
            }
        }
    }
}
