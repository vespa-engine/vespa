// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.FileReference;
import com.yahoo.io.IOUtils;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.net.HostName;
import com.yahoo.vespa.filedistribution.FileDownloader;
import com.yahoo.vespa.filedistribution.FileReferenceCompressor;
import com.yahoo.vespa.filedistribution.FileReferenceData;
import com.yahoo.vespa.filedistribution.FileReferenceDownload;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.yahoo.vespa.filedistribution.FileReferenceData.CompressionType.gzip;
import static com.yahoo.vespa.filedistribution.FileReferenceData.CompressionType.lz4;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FileServerTest {

    private FileServer fileServer;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setup() throws IOException {
        File rootDir = new File(temporaryFolder.newFolder("fileserver-root").getAbsolutePath());
        fileServer = new FileServer(rootDir, new MockFileDownloader(rootDir), List.of(gzip, lz4));
    }

    @Test
    public void requireThatExistingFileIsFound() throws IOException {
        String dir = "123";
        writeFile(dir);
        assertTrue(fileServer.hasFile(dir));
    }

    @Test
    public void requireThatNonExistingFileIsNotFound() {
        assertFalse(fileServer.hasFile("12x"));
    }

    @Test
    public void requireThatNonExistingFileWillBeDownloaded() throws IOException {
        String dir = "123";
        assertFalse(fileServer.hasFile(dir));
        FileReferenceDownload foo = new FileReferenceDownload(new FileReference(dir), "test");
        assertFalse(fileServer.hasFileDownloadIfNeeded(foo));
        writeFile(dir);
        assertTrue(fileServer.hasFileDownloadIfNeeded(foo));
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
        fileServer.startFileServing(new FileReference("12y"), new FileReceiver(content), Set.of(gzip));
        assertEquals(new String(content.get()), "dummy-data");
    }

    @Test
    public void requireThatWeCanReplayDirWithLz4() throws IOException, InterruptedException, ExecutionException {
        File rootDir = new File(temporaryFolder.newFolder("fileserver-root-3").getAbsolutePath());
        fileServer = new FileServer(rootDir, new MockFileDownloader(rootDir), List.of(lz4, gzip)); // prefer lz4
        File dir = getFileServerRootDir();
        IOUtils.writeFile(dir + "/subdir/12z/f1", "dummy-data-2", true);
        CompletableFuture<byte []> content = new CompletableFuture<>();
        fileServer.startFileServing(new FileReference("subdir"), new FileReceiver(content), Set.of(gzip, lz4));

        // Decompress with lz4 and check contents
        var compressor = new FileReferenceCompressor(FileReferenceData.Type.compressed, lz4);
        File downloadedFileCompressed = new File(dir + "/downloaded-file-compressed");
        IOUtils.writeFile(downloadedFileCompressed, content.get());
        File downloadedFileUncompressed = new File(dir + "/downloaded-file-uncompressed");
        compressor.decompress(downloadedFileCompressed, downloadedFileUncompressed);
        assertTrue(downloadedFileUncompressed.isDirectory());
        assertEquals("dummy-data-2", IOUtils.readFile(new File(downloadedFileUncompressed, "12z/f1")));
    }

    @Test
    public void requireThatDifferentNumberOfConfigServersWork() throws IOException {
        // Empty connection pool in tests etc.
        ConfigserverConfig.Builder builder = new ConfigserverConfig.Builder()
                .configServerDBDir(temporaryFolder.newFolder("serverdb").getAbsolutePath())
                .configDefinitionsDir(temporaryFolder.newFolder("configdefinitions").getAbsolutePath());
        FileServer fileServer = createFileServer(builder);
        assertEquals(0, fileServer.downloader().connectionPool().getSize());

        // Empty connection pool when only one server, no use in downloading from yourself
        List<ConfigserverConfig.Zookeeperserver.Builder> servers = new ArrayList<>();
        ConfigserverConfig.Zookeeperserver.Builder serverBuilder = new ConfigserverConfig.Zookeeperserver.Builder();
        serverBuilder.hostname(HostName.getLocalhost());
        serverBuilder.port(123456);
        servers.add(serverBuilder);
        builder.zookeeperserver(servers);
        fileServer = createFileServer(builder);
        assertEquals(0, fileServer.downloader().connectionPool().getSize());

        // connection pool of size 1 when 2 servers
        ConfigserverConfig.Zookeeperserver.Builder serverBuilder2 = new ConfigserverConfig.Zookeeperserver.Builder();
        serverBuilder2.hostname("bar");
        serverBuilder2.port(123456);
        servers.add(serverBuilder2);
        builder.zookeeperserver(servers);
        fileServer = createFileServer(builder);
        assertEquals(1, fileServer.downloader().connectionPool().getSize());
    }

    private void writeFile(String dir) throws IOException {
        File rootDir = getFileServerRootDir();
        IOUtils.createDirectory(rootDir + "/" + dir);
        IOUtils.writeFile(rootDir + "/" + dir + "/f1", "test", false);
    }

    private FileServer createFileServer(ConfigserverConfig.Builder configBuilder) throws IOException {
        File fileReferencesDir = temporaryFolder.newFolder();
        configBuilder.fileReferencesDir(fileReferencesDir.getAbsolutePath());
        return new FileServer(new ConfigserverConfig(configBuilder), new InMemoryFlagSource());
    }

    private static class FileReceiver implements FileServer.Receiver {
        final CompletableFuture<byte []> content;
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

    private static class MockFileDownloader extends FileDownloader {

        public MockFileDownloader(File downloadDirectory) {
            super(FileDownloader.emptyConnectionPool(),
                  new Supervisor(new Transport("mock")).setDropEmptyBuffers(true),
                  downloadDirectory,
                  Duration.ofMillis(100),
                  Duration.ofMillis(100),
                  Set.of(gzip));
        }

    }

}
