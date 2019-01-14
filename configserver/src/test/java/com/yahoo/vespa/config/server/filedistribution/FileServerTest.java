// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.io.IOUtils;
import com.yahoo.net.HostName;
import com.yahoo.vespa.config.server.SimpleJrtFactory;
import com.yahoo.vespa.filedistribution.FileReferenceData;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class FileServerTest {

    private FileServer fileServer;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setup() throws IOException {
        File rootDir = new File(temporaryFolder.newFolder("fileserver-root").getAbsolutePath());
        fileServer = new FileServer(rootDir, new SimpleJrtFactory());
    }

    @Test
    public void requireThatExistingFileCanBeFound() throws IOException {
        File dir = getFileServerRootDir();
        IOUtils.createDirectory(dir + "/123");
        IOUtils.writeFile(dir + "/123/f1", "test", false);
        assertTrue(fileServer.hasFile("123"));
    }

    @Test
    public void requireThatNonExistingFileCanNotBeFound() {
        assertFalse(fileServer.hasFile("12x"));
    }

    @Test
    public void requireThatFileReferenceWithDirectoryCanBeFound() throws IOException {
        File dir = getFileServerRootDir();
        IOUtils.writeFile(dir + "/124/subdir/f1", "test", false);
        IOUtils.writeFile(dir + "/124/subdir/f2", "test", false);
        assertTrue(fileServer.hasFile("124/subdir"));
    }

    @Test
    public void requireThatWeCanReplayFile() throws IOException, InterruptedException, ExecutionException {
        File dir = getFileServerRootDir();
        IOUtils.writeFile(dir + "/12y/f1", "dummy-data", true);
        CompletableFuture<byte []> content = new CompletableFuture<>();
        fileServer.startFileServing("12y", new FileReceiver(content));
        assertEquals(new String(content.get()), "dummy-data");
    }

    @Test
    public void requireThatDifferentNumberOfConfigServersWork() throws IOException {
        // Empty connection pool in tests etc.
        ConfigserverConfig.Builder builder = new ConfigserverConfig.Builder()
                .configServerDBDir(temporaryFolder.newFolder("serverdb").getAbsolutePath())
                .configDefinitionsDir(temporaryFolder.newFolder("configdefinitions").getAbsolutePath());
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

    private FileServer createFileServer(ConfigserverConfig.Builder configBuilder) throws IOException {
        File fileReferencesDir = temporaryFolder.newFolder();
        configBuilder.fileReferencesDir(fileReferencesDir.getAbsolutePath());
        return new FileServer(new ConfigserverConfig(configBuilder), new SimpleJrtFactory());
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

    private File getFileServerRootDir() {
        return fileServer.getRootDir().getRoot();
    }

}
