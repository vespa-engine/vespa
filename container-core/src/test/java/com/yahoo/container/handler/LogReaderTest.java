package com.yahoo.container.handler;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.Assert.*;

public class LogReaderTest {

    ByteArrayOutputStream outputStream;

    @Before
    public void setup() {
        outputStream = new ByteArrayOutputStream();
    }

    @Test
    public void testThatFilesAreWrittenCorrectlyToOutputStream() throws Exception{
        String logDirectory = "src/test/resources/logfolder/";
        LogReader.writeToOutputStream(logDirectory, outputStream);
        String expected = "{\"subfolder\":{\"log2.log\":\"VGhpcyBpcyBhbm90aGVyIGxvZyBmaWxl\"},\"log1.log\":\"VGhpcyBpcyBvbmUgbG9nIGZpbGU=\"}";
        String actual = new String(outputStream.toByteArray());
        assertEquals(expected, actual);
    }

    @Test
    public void testNothingISWrittenToOutputStreamWithEmptyLogFolder() throws Exception {
        String logDirectory = "src/test/resources/emptylogfolder/";
        LogReader.writeToOutputStream(logDirectory, outputStream);
        String expected = "{}";
        String actual = new String(outputStream.toByteArray());
        assertEquals(expected, actual);
    }
}