// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io;

import org.junit.Test;

import java.io.*;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class IOUtilsTestCase {

    @Test
    public void testCloseNUllDoesNotFail() {
        IOUtils.closeWriter(null);
        IOUtils.closeReader(null);
        IOUtils.closeInputStream(null);
        IOUtils.closeOutputStream(null);
    }

    @Test
    public void testFileWriter() throws IOException {
        IOUtils.writeFile("temp1.txt", "hello",false);
        assertEquals("hello", IOUtils.readFile(new File("temp1.txt")));
        new File("temp1.txt").delete();
    }

    @Test
    public void testFileWriterWithoutEncoding() throws IOException {
        BufferedWriter writer=null;
        try {
            writer=IOUtils.createWriter(new File("temp2.txt"),false);
            writer.write("hello");
        }
        finally {
            IOUtils.closeWriter(writer);
        }
        assertEquals("hello", IOUtils.readFile(new File("temp2.txt")));
        new File("temp2.txt").delete();
    }

    @Test
    public void testFileWriterWithoutEncodingFromFileName() throws IOException {
        BufferedWriter writer=null;
        try {
            writer=IOUtils.createWriter("temp3.txt",false);
            writer.write("hello");
        }
        finally {
            IOUtils.closeWriter(writer);
        }
        assertEquals("hello",IOUtils.readFile(new File("temp3.txt")));
        new File("temp3.txt").delete();
    }

    @Test
    public void testFileCounting() throws IOException {
        IOUtils.writeFile("temp4.txt","hello\nworld",false);
        assertEquals(2,IOUtils.countLines("temp4.txt"));
        new File("temp4.txt").delete();
    }

    @Test
    public void testFileCopy() throws IOException {
        IOUtils.writeFile("temp5.txt","hello",false);
        IOUtils.copy(new File("temp5.txt"), new File("temp5copy.txt"));
        assertEquals("hello", IOUtils.readFile(new File("temp5copy.txt")));
        new File("temp5.txt").delete();
        new File("temp5copy.txt").delete();
    }

    @Test
    public void testFileCopyWithLineCap() throws IOException {
        IOUtils.writeFile("temp6.txt","hello\nyou\nworld",false);
        IOUtils.copy("temp6.txt","temp6copy.txt",2);
        assertEquals("hello\nyou\n", IOUtils.readFile(new File("temp6copy.txt")));
        new File("temp6.txt").delete();
        new File("temp6copy.txt").delete();
    }

    @Test
    public void testGetLines() throws IOException {
        IOUtils.writeFile("temp7.txt","hello\nworld",false);
        List<String> lines=IOUtils.getLines("temp7.txt");
        assertEquals(2,lines.size());
        assertEquals("hello",lines.get(0));
        assertEquals("world",lines.get(1));
        new File("temp7.txt").delete();
    }

    @Test
    public void testFileWriterAppend() throws IOException {
        boolean append=true;
        IOUtils.writeFile("temp8.txt", "hello",!append);
        BufferedWriter writer=null;
        try {
            writer=IOUtils.createWriter(new File("temp8.txt"),append);
            writer.write("\nworld");
        }
        finally {
            IOUtils.closeWriter(writer);
        }
        assertEquals("hello\nworld", IOUtils.readFile(new File("temp8.txt")));
        new File("temp8.txt").delete();
    }

    @Test
    public void testCloseAllReaders() throws IOException {
        StringReader reader1=new StringReader("hello");
        StringReader reader2=new StringReader("world");
        IOUtils.closeAll(Arrays.<Reader>asList(reader1, reader2));
        try {
            reader1.ready();
            fail("Expected exception due to reader closed");
        }
        catch (IOException e) {
            // Expected
        }
        try {
            reader2.ready();
            fail("Expected exception due to reader closed");
        }
        catch (IOException e) {
            // Expected
        }
    }

    @Test
    public void testDirCopying() throws IOException {
        IOUtils.writeFile("temp1/temp1.txt","hello",false);
        IOUtils.writeFile("temp1/temp2.txt","world",false);
        IOUtils.copyDirectory(new File("temp1"), new File("temp2"));
        assertEquals("hello", IOUtils.readFile(new File("temp2/temp1.txt")));
        assertEquals("world", IOUtils.readFile(new File("temp2/temp2.txt")));
        IOUtils.recursiveDeleteDir(new File("temp1"));
        IOUtils.recursiveDeleteDir(new File("temp2"));
        assertTrue(!new File("temp1").exists());
        assertTrue(!new File("temp2").exists());
    }

    @Test
    public void testDirCopyingWithFilter() throws IOException {
        IOUtils.writeFile("temp1/temp1.txt","hello",false);
        IOUtils.writeFile("temp1/temp2.txt","world",false);
        IOUtils.writeFile("temp1/temp3.json", "world", false);
        IOUtils.copyDirectory(new File("temp1"), new File("temp2"), -1, new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return name.endsWith(".json");
                    }
                });
        assertEquals("world", IOUtils.readFile(new File("temp2/temp3.json")));
        assertFalse(new File("temp2/temp1.txt").exists());
        assertFalse(new File("temp2/temp2.txt").exists());
        IOUtils.recursiveDeleteDir(new File("temp1"));
        IOUtils.recursiveDeleteDir(new File("temp2"));
        assertTrue(!new File("temp1").exists());
        assertTrue(!new File("temp2").exists());
    }

}
