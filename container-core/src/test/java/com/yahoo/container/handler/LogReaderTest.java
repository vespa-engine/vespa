package com.yahoo.container.handler;

import org.json.JSONObject;
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
        LogReader logReader = new LogReader(2);
        JSONObject json = logReader.readLogs(logDirectory);
        String expected = "{\"subfolder\":{\"log2.log\":\"VGhpcyBpcyBhbm90aGVyIGxvZyBmaWxl\"},\"log1.log\":\"VGhpcyBpcyBvbmUgbG9nIGZpbGU=\"}";
        String actual = json.toString();
        assertEquals(expected, actual);
    }
}