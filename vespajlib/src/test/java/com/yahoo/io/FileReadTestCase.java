// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FileReadTestCase {

    @Test
    public void testReadByteArray() throws IOException {
        byte[] thisFile = IOUtils.readFileBytes(new File("src/test/java/com/yahoo/io/FileReadTestCase.java"));
        String str = new String(thisFile, Charset.forName("US-ASCII"));
        assertTrue(str.startsWith("// Copyright 2017 Yahoo Holdings."));
        assertTrue(str.endsWith("// Yeppers\n"));
    }

    @Test
    public void testReadString() throws IOException {
        String str = IOUtils.readFile(new File("src/test/java/com/yahoo/io/FileReadTestCase.java"));
        assertTrue(str.startsWith("// Copyright 2017 Yahoo Holdings."));
        assertTrue(str.endsWith("// Yeppers\n"));
    }

    @Test
    public void testReadAllFromReader() throws IOException {
        assertEquals(IOUtils.readAll(new StringReader("")), "");
        assertEquals(IOUtils.readAll(new StringReader("hei")), "hei");
        assertEquals(IOUtils.readAll(new StringReader("hei\nhaa")), "hei\nhaa");
        assertEquals(IOUtils.readAll(new StringReader("hei\nhaa\n")), "hei\nhaa\n");
    }

}

// Yeppers
