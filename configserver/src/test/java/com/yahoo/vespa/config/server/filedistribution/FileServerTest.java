// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.io.IOUtils;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
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

    private static class FileReceiver implements FileServer.Receiver {
        CompletableFuture<byte []> content;
        FileReceiver(CompletableFuture<byte []> content) {
            this.content = content;
        }
        @Override
        public void receive(FileReference reference, String filename, byte[] content, FileServer.ReplayStatus status) {
            this.content.complete(content);
        }
    }
    @Test
    public void requireThatWeCanReplayFile() throws IOException, InterruptedException, ExecutionException {
        createCleanDir("12y");
        IOUtils.writeFile("12y/f1", "dummy-data", true);
        CompletableFuture<byte []> content = new CompletableFuture<>();
        fs.startFileServing("12y", new FileReceiver(content));
        assertEquals(new String(content.get()), "dummy-data");
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
