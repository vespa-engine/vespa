package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.io.IOUtils;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class FileServerTest {

    FileServer fs = new FileServer(".");
    List<File> created = new LinkedList<>();

    private void createCleanDir(String name) throws  IOException{
        File dir = new File(name);
        IOUtils.recursiveDeleteDir(dir);
        IOUtils.createDirectory(dir.getName());
        File dummy = new File(dir.getName() +"/dummy");
        IOUtils.writeFile(dummy, "test", true);
        assertTrue(dummy.delete());
        created.add(dir);
    }

    @Test
    public void requireThatExistingFileCanbeFound() throws IOException {
        createCleanDir("123");
        IOUtils.writeFile("123/f1", "test", true);
        assertTrue(fs.hasFile("123"));
        cleanup();
    }

    @Test
    public void requireThatNonExistingFileCanNotBeFound() throws IOException {
        assertFalse(fs.hasFile("12x"));
        createCleanDir("12x");
        assertFalse(fs.hasFile("12x"));
        cleanup();
    }

    private void cleanup() {
        created.forEach((file) -> IOUtils.recursiveDeleteDir(file));
        created.clear();
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        cleanup();
    }

}
