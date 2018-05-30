// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.google.common.io.Files;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.io.IOUtils;
import com.yahoo.net.HostName;
import com.yahoo.vespa.filedistribution.FileReferenceData;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class FileServerTest {

    private FileServer fs = new FileServer(new File("."));
    private List<File> created = new LinkedList<>();

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
    public void requireThatExistingFileCanBeFound() throws IOException {
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

    @Test
    public void requireThatFileReferenceWithDirectoryCanBeFound() throws IOException {
        createCleanDir("124/subdir");
        IOUtils.writeFile("124/subdir/f1", "test", false);
        IOUtils.writeFile("124/subdir/f2", "test", false);
        assertTrue(fs.hasFile("124/subdir"));
        cleanup();
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

    @Test
    public void requireThatDifferentNumberOfConfigServersWork() {
        // Empty connection pool in tests etc.
        ConfigserverConfig.Builder builder = new ConfigserverConfig.Builder()
                .configServerDBDir(Files.createTempDir().getAbsolutePath())
                .configDefinitionsDir(Files.createTempDir().getAbsolutePath());
        FileServer fileServer = createFileServer(builder);
        assertEquals(0, fileServer.downloader().fileReferenceDownloader().connectionPool().getSize());

        // Empty connection pool when only one server, no use in downloading from yourself
        List<ConfigserverConfig.Zookeeperserver.Builder> servers = new ArrayList<>();
        ConfigserverConfig.Zookeeperserver.Builder serverBuilder = new ConfigserverConfig.Zookeeperserver.Builder();
        serverBuilder.hostname(HostName.getLocalhost());
        serverBuilder.port(123456);
        servers.add(serverBuilder);
        builder.zookeeperserver(servers);
        fileServer = createFileServer(builder);
        assertEquals(0, fileServer.downloader().fileReferenceDownloader().connectionPool().getSize());

        // connection pool of size 1 when 2 servers
        ConfigserverConfig.Zookeeperserver.Builder serverBuilder2 = new ConfigserverConfig.Zookeeperserver.Builder();
        serverBuilder2.hostname("bar");
        serverBuilder2.port(123456);
        servers.add(serverBuilder2);
        builder.zookeeperserver(servers);
        fileServer = createFileServer(builder);
        assertEquals(1, fileServer.downloader().fileReferenceDownloader().connectionPool().getSize());
    }

    private FileServer createFileServer(ConfigserverConfig.Builder configBuilder) {
        File fileReferencesDir = Files.createTempDir();
        configBuilder.fileReferencesDir(fileReferencesDir.getAbsolutePath());
        return new FileServer(new ConfigserverConfig(configBuilder));
    }

    private static class FileReceiver implements FileServer.Receiver {
        CompletableFuture<byte []> content;
        FileReceiver(CompletableFuture<byte []> content) {
            this.content = content;
        }
        @Override
        public void receive(FileReferenceData fileData, FileServer.ReplayStatus status) {
            this.content.complete(fileData.content().array());
        }
    }

    @After
    public void cleanup() {
        created.forEach(IOUtils::recursiveDeleteDir);
        created.clear();
    }

}
