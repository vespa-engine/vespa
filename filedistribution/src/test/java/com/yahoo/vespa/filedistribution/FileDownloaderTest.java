// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.io.IOUtils;
import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.RequestWaiter;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.Connection;
import com.yahoo.vespa.config.ConnectionPool;
import net.jpountz.xxhash.XXHash64;
import net.jpountz.xxhash.XXHashFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.yahoo.jrt.ErrorCode.CONNECTION;
import static com.yahoo.vespa.filedistribution.FileReferenceData.CompressionType.gzip;
import static com.yahoo.vespa.filedistribution.FileReferenceData.Type.compressed;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FileDownloaderTest {
    private static final Duration sleepBetweenRetries = Duration.ofMillis(10);
    private static final Set<FileReferenceData.CompressionType> acceptedCompressionTypes = Set.of(gzip);

    private MockConnection connection;
    private FileDownloader fileDownloader;
    private File downloadDir;
    private Supervisor supervisor;

    @Before
    public void setup() {
        try {
            downloadDir = Files.createTempDirectory("filedistribution").toFile();
            connection = new MockConnection();
            supervisor = new Supervisor(new Transport()).setDropEmptyBuffers(true);
            fileDownloader = createDownloader(connection, Duration.ofSeconds(1));
        } catch (IOException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @After
    public void teardown() {
        fileDownloader.close();
        supervisor.transport().shutdown().join();
    }

    @Test
    public void getFile() throws IOException {
        File downloadDir = fileDownloader.downloadDirectory();

        {
            // fileReference already exists on disk, does not have to be downloaded

            String fileReferenceString = "foo";
            String filename = "foo.jar";
            FileReference fileReference = new FileReference(fileReferenceString);
            File fileReferenceFullPath = fileReferenceFullPath(downloadDir, fileReference);
            writeFileReference(downloadDir, fileReferenceString, filename);
            fileDownloader.downloads().completedDownloading(fileReference, fileReferenceFullPath);

            // Check that we get correct path and content when asking for file reference
            Optional<File> pathToFile = getFile(fileReference);
            assertTrue(pathToFile.isPresent());
            String downloadedFile = new File(fileReferenceFullPath, filename).getAbsolutePath();
            assertEquals(new File(fileReferenceFullPath, filename).getAbsolutePath(), downloadedFile);
            assertEquals("content", IOUtils.readFile(pathToFile.get()));

            // Verify download status when downloaded
            assertDownloadStatus(fileReference, 1.0);
        }

        {
            // fileReference does not exist on disk, needs to be downloaded, but fails when asking upstream for file)

            connection.setResponseHandler(new MockConnection.UnknownFileReferenceResponseHandler());

            FileReference fileReference = new FileReference("bar");
            File fileReferenceFullPath = fileReferenceFullPath(downloadDir, fileReference);
            assertFalse(fileReferenceFullPath.getAbsolutePath(), getFile(fileReference).isPresent());

            // Verify download status when unable to download
            assertDownloadStatus(fileReference, 0.0);
        }

        {
            // fileReference does not exist on disk, needs to be downloaded)

            FileReference fileReference = new FileReference("baz");
            File fileReferenceFullPath = fileReferenceFullPath(downloadDir, fileReference);
            assertFalse(fileReferenceFullPath.getAbsolutePath(), getFile(fileReference).isPresent());

            // Verify download status
            assertDownloadStatus(fileReference, 0.0);

            // Receives fileReference, should return and make it available to caller
            String filename = "abc.jar";
            receiveFile(fileReference, filename, FileReferenceData.Type.file, "some other content");
            Optional<File> downloadedFile = getFile(fileReference);

            assertTrue(downloadedFile.isPresent());
            File downloadedFileFullPath = new File(fileReferenceFullPath, filename);
            assertEquals(downloadedFileFullPath.getAbsolutePath(), downloadedFile.get().getAbsolutePath());
            assertEquals("some other content", IOUtils.readFile(downloadedFile.get()));

            // Verify download status when downloaded
            System.out.println(fileDownloader.downloads().downloadStatuses());
            assertDownloadStatus(fileReference, 1.0);
        }

        {
            // fileReference does not exist on disk, needs to be downloaded, is compressed data

            FileReference fileReference = new FileReference("fileReferenceToDirWithManyFiles");
            File fileReferenceFullPath = fileReferenceFullPath(downloadDir, fileReference);
            assertFalse(fileReferenceFullPath.getAbsolutePath(), getFile(fileReference).isPresent());

            // Verify download status
            assertDownloadStatus(fileReference, 0.0);

            // Receives fileReference, should return and make it available to caller
            String filename = "abc.tar.gz";
            Path tempPath = Files.createTempDirectory("dir");
            File subdir = new File(tempPath.toFile(), "subdir");
            File fooFile = new File(subdir, "foo");
            IOUtils.writeFile(fooFile, "foo", false);
            // Check that long file names work. (need to do TarArchiveOutPutStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)) for it to work);
            File barFile = new File(subdir, "really-long-filename-over-100-bytes-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
            IOUtils.writeFile(barFile, "bar", false);

            File tarFile = new FileReferenceCompressor(compressed, gzip).compress(tempPath.toFile(), Arrays.asList(fooFile, barFile), new File(tempPath.toFile(), filename));
            byte[] tarredContent = IOUtils.readFileBytes(tarFile);
            receiveFile(fileReference, filename, compressed, tarredContent);
            Optional<File> downloadedFile = getFile(fileReference);

            assertTrue(downloadedFile.isPresent());
            File downloadedFoo = new File(fileReferenceFullPath, tempPath.relativize(fooFile.toPath()).toString());
            File downloadedBar = new File(fileReferenceFullPath, tempPath.relativize(barFile.toPath()).toString());
            assertEquals("foo", IOUtils.readFile(downloadedFoo));
            assertEquals("bar", IOUtils.readFile(downloadedBar));

            // Verify download status when downloaded
            assertDownloadStatus(fileReference, 1.0);
        }
    }

    @Test
    public void getFileWhenConnectionError() throws IOException {
        fileDownloader = createDownloader(connection, Duration.ofSeconds(2));
        File downloadDir = fileDownloader.downloadDirectory();

        int timesToFail = 2;
        MockConnection.ConnectionErrorResponseHandler responseHandler = new MockConnection.ConnectionErrorResponseHandler(timesToFail);
        connection.setResponseHandler(responseHandler);

        FileReference fileReference = new FileReference("fileReference");
        File fileReferenceFullPath = fileReferenceFullPath(downloadDir, fileReference);
        assertFalse(fileReferenceFullPath.getAbsolutePath(), getFile(fileReference).isPresent());

        // Getting file failed, verify download status and since there was an error is not downloading ATM
        assertDownloadStatus(fileReference, 0.0);
        assertFalse(fileDownloader.isDownloading(fileReference));

        // Receives fileReference, should return and make it available to caller
        String filename = "abc.jar";
        receiveFile(fileReference, filename, FileReferenceData.Type.file, "some other content");
        Optional<File> downloadedFile = getFile(fileReference);
        assertTrue(downloadedFile.isPresent());
        File downloadedFileFullPath = new File(fileReferenceFullPath, filename);
        assertEquals(downloadedFileFullPath.getAbsolutePath(), downloadedFile.get().getAbsolutePath());
        assertEquals("some other content", IOUtils.readFile(downloadedFile.get()));

        // Verify download status when downloaded
        assertDownloadStatus(fileReference, 1.0);

        assertEquals(timesToFail, responseHandler.failedTimes);
    }

    @Test
    public void getFileWhenDownloadInProgress() throws IOException, ExecutionException, InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        String filename = "abc.jar";
        fileDownloader = createDownloader(connection, Duration.ofSeconds(3));
        File downloadDir = fileDownloader.downloadDirectory();

        // Delay response so that we can make a second request while downloading the file from the first request
        connection.setResponseHandler(new MockConnection.WaitResponseHandler(Duration.ofSeconds(1)));

        FileReference fileReference = new FileReference("fileReference123");
        File fileReferenceFullPath = fileReferenceFullPath(downloadDir, fileReference);
        FileReferenceDownload fileReferenceDownload = new FileReferenceDownload(fileReference, "test");

        Future<Future<Optional<File>>> future1 = executor.submit(() -> fileDownloader.getFutureFile(fileReferenceDownload));
        do {
            Thread.sleep(10);
        } while (! fileDownloader.isDownloading(fileReference));
        assertTrue(fileDownloader.isDownloading(fileReference));

        // Request file while download is in progress
        Future<Future<Optional<File>>> future2 = executor.submit(() -> fileDownloader.getFutureFile(fileReferenceDownload));

        // Receive file, will complete downloading and futures
        receiveFile(fileReference, filename, FileReferenceData.Type.file, "some other content");

        // Check that we got file correctly with first request
        Optional<File> downloadedFile = future1.get().get();
        assertTrue(downloadedFile.isPresent());
        File downloadedFileFullPath = new File(fileReferenceFullPath, filename);
        assertEquals(downloadedFileFullPath.getAbsolutePath(), downloadedFile.get().getAbsolutePath());
        assertEquals("some other content", IOUtils.readFile(downloadedFile.get()));

        // Check that request done while downloading works
        downloadedFile = future2.get().get();
        assertTrue(downloadedFile.isPresent());
        executor.shutdownNow();
    }

    @Test
    public void setFilesToDownload() {
        Duration timeout = Duration.ofMillis(200);
        MockConnection connectionPool = new MockConnection();
        connectionPool.setResponseHandler(new MockConnection.WaitResponseHandler(timeout.plus(Duration.ofMillis(1000))));
        FileDownloader fileDownloader = createDownloader(connectionPool, timeout);
        FileReference xyzzy = new FileReference("xyzzy");
        // Should download since we do not have the file on disk
        fileDownloader.downloadIfNeeded(new FileReferenceDownload(xyzzy, "test"));
        assertTrue(fileDownloader.isDownloading(xyzzy));
        assertFalse(getFile(xyzzy).isPresent());
        // Receive files to simulate download
        receiveFile(xyzzy, "xyzzy.jar", FileReferenceData.Type.file, "content");
        // Should not download, since file has already been downloaded
        fileDownloader.downloadIfNeeded(new FileReferenceDownload(xyzzy, "test"));
        // and file should be available
        assertTrue(getFile(xyzzy).isPresent());
    }

    @Test
    public void receiveFile() throws IOException {
        FileReference foobar = new FileReference("foobar");
        String filename = "foo.jar";
        receiveFile(foobar, filename, FileReferenceData.Type.file, "content");
        File downloadedFile = new File(fileReferenceFullPath(downloadDir, foobar), filename);
        assertEquals("content", IOUtils.readFile(downloadedFile));
    }

    @Test
    public void testCompressionTypes() {
        try {
            createDownloader(connection, Duration.ofSeconds(1), Set.of());
            fail("expected to fail when set is empty");
        } catch (IllegalArgumentException e) {
            // ignore
        }
    }

    private void writeFileReference(File dir, String fileReferenceString, String fileName) throws IOException {
        File fileReferenceDir = new File(dir, fileReferenceString);
        fileReferenceDir.mkdir();
        File file = new File(fileReferenceDir, fileName);
        IOUtils.writeFile(file, "content", false);
    }

    private File fileReferenceFullPath(File dir, FileReference fileReference) {
        return new File(dir, fileReference.value());
    }

    private void assertDownloadStatus(FileReference fileReference, double expectedDownloadStatus) {
        Downloads downloads = fileDownloader.downloads();
        double downloadStatus = downloads.downloadStatus(fileReference);
        assertEquals("Download statuses: " + downloads.downloadStatuses().toString(),
                     expectedDownloadStatus,
                     downloadStatus,
                     0.0001);
    }

    private void receiveFile(FileReference fileReference, String filename, FileReferenceData.Type type, String content) {
        receiveFile(fileReference, filename, type, Utf8.toBytes(content));
    }

    private void receiveFile(FileReference fileReference, String filename,
                             FileReferenceData.Type type, byte[] content) {
        XXHash64 hasher = XXHashFactory.fastestInstance().hash64();
        FileReceiver.Session session =
                new FileReceiver.Session(downloadDir, 1, fileReference, type, gzip, filename, content.length);
        session.addPart(0, content);
        File file = session.close(hasher.hash(ByteBuffer.wrap(content), 0));
        fileDownloader.downloads().completedDownloading(fileReference, file);
    }

    private Optional<File> getFile(FileReference fileReference) {
        return fileDownloader.getFile(new FileReferenceDownload(fileReference, "test"));
    }

    private FileDownloader createDownloader(MockConnection connection, Duration timeout) {
        return  createDownloader(connection, timeout, acceptedCompressionTypes);
    }

    private FileDownloader createDownloader(MockConnection connection, Duration timeout, Set<FileReferenceData.CompressionType> acceptedCompressionTypes) {
        return new FileDownloader(connection, supervisor, downloadDir, timeout, sleepBetweenRetries, acceptedCompressionTypes);
    }

    private static class MockConnection implements ConnectionPool, com.yahoo.vespa.config.Connection {

        private ResponseHandler responseHandler;

        MockConnection() {
            this(new FileReferenceFoundResponseHandler());
        }

        MockConnection(ResponseHandler responseHandler) {
            this.responseHandler = responseHandler;
        }

        @Override
        public void invokeAsync(Request request, Duration jrtTimeout, RequestWaiter requestWaiter) {
            responseHandler.request(request);
        }

        @Override
        public void invokeSync(Request request, Duration jrtTimeout) {
            responseHandler.request(request);
        }

        @Override
        public String getAddress() {
            return null;
        }

        @Override
        public void close() {
        }

        @Override
        public Connection getCurrent() {
            return this;
        }

        @Override
        public Connection switchConnection(Connection connection) {
            return this;
        }

        @Override
        public int getSize() {
            return 1;
        }

        void setResponseHandler(ResponseHandler responseHandler) {
            this.responseHandler = responseHandler;
        }

        public interface ResponseHandler {
            void request(Request request);
        }

        static class FileReferenceFoundResponseHandler implements MockConnection.ResponseHandler {

            @Override
            public void request(Request request) {
                if (request.methodName().equals("filedistribution.serveFile")) {
                    request.returnValues().add(new Int32Value(0));
                    request.returnValues().add(new StringValue("OK"));
                }
            }
        }

        static class UnknownFileReferenceResponseHandler implements MockConnection.ResponseHandler {

            @Override
            public void request(Request request) {
                if (request.methodName().equals("filedistribution.serveFile")) {
                    request.returnValues().add(new Int32Value(1));
                    request.returnValues().add(new StringValue("Internal error"));
                }
            }
        }

        static class WaitResponseHandler implements MockConnection.ResponseHandler {

            private final Duration waitUntilAnswering;

            WaitResponseHandler(Duration waitUntilAnswering) {
                super();
                this.waitUntilAnswering = waitUntilAnswering;
            }

            @Override
            public void request(Request request) {
                try { Thread.sleep(waitUntilAnswering.toMillis());} catch (InterruptedException e) { /* do nothing */ }

                if (request.methodName().equals("filedistribution.serveFile")) {
                    request.returnValues().add(new Int32Value(0));
                    request.returnValues().add(new StringValue("OK"));
                }
            }
        }

        static class ConnectionErrorResponseHandler implements MockConnection.ResponseHandler {

            private final int timesToFail;
            private int failedTimes = 0;

            ConnectionErrorResponseHandler(int timesToFail) {
                super();
                this.timesToFail = timesToFail;
            }

            @Override
            public void request(Request request) {
                if (request.methodName().equals("filedistribution.serveFile")) {
                    if (failedTimes < timesToFail) {
                        request.setError(CONNECTION, "Connection error");
                        failedTimes++;
                    } else {
                        request.returnValues().add(new Int32Value(0));
                        request.returnValues().add(new StringValue("OK"));
                    }
                }
            }
        }
    }

}
