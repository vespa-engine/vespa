package com.yahoo.vespa.config.proxy.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.io.IOUtils;
import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.RequestWaiter;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.Connection;
import com.yahoo.vespa.config.ConnectionPool;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FileDownloaderTest {

    private MockConnection connection;
    private FileDownloader fileDownloader;

    @Before
    public void setup() {
        try {
            File downloadDir = Files.createTempDirectory("filedistribution").toFile();
            connection = new MockConnection();
            fileDownloader = new FileDownloader(connection, downloadDir, Duration.ofMillis(3000));
        } catch (IOException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void getFile() throws IOException {
        File downloadDir = fileDownloader.downloadDirectory();

        {
            // fileReference already exists on disk, does not have to be downloaded

            String fileReferenceString = "foo";
            String filename = "foo.jar";
            File fileReferenceFullPath = fileReferenceFullPath(downloadDir, fileReferenceString);
            FileReference fileReference = new FileReference(fileReferenceString);
            writeFileReference(downloadDir, fileReferenceString, filename);

            // Check that we get correct path and content when asking for file reference
            Optional<File> pathToFile = fileDownloader.getFile(fileReference);
            assertTrue(pathToFile.isPresent());
            String downloadedFile = new File(fileReferenceFullPath, filename).getAbsolutePath();
            assertEquals(new File(fileReferenceFullPath, filename).getAbsolutePath(), downloadedFile);
            assertEquals("content", IOUtils.readFile(pathToFile.get()));

            // Verify download status when downloaded
            assertDownloadStatus(fileDownloader, fileReference, 100.0);
        }

        {
            // fileReference does not exist on disk, needs to be downloaded, but fails when asking upstream for file)

            connection.setResponseHandler(new MockConnection.UnknownFileReferenceResponseHandler());

            FileReference fileReference = new FileReference("bar");
            File fileReferenceFullPath = fileReferenceFullPath(downloadDir, fileReference.value());
            assertFalse(fileReferenceFullPath.getAbsolutePath(), fileDownloader.getFile(fileReference).isPresent());

            // Verify download status when unable to download
            assertDownloadStatus(fileDownloader, fileReference, 0.0);
        }

        {
            // fileReference does not exist on disk, needs to be downloaded)

            FileReference fileReference = new FileReference("fileReference");
            File fileReferenceFullPath = fileReferenceFullPath(downloadDir, fileReference.value());
            assertFalse(fileReferenceFullPath.getAbsolutePath(), fileDownloader.getFile(fileReference).isPresent());

            // Verify download status
            assertDownloadStatus(fileDownloader, fileReference, 0.0);

            // Receives fileReference, should return and make it available to caller
            String filename = "abc.jar";
            fileDownloader.receiveFile(fileReference, filename, Utf8.toBytes("some other content"));
            Optional<File> downloadedFile = fileDownloader.getFile(fileReference);

            assertTrue(downloadedFile.isPresent());
            File downloadedFileFullPath = new File(fileReferenceFullPath, filename);
            assertEquals(downloadedFileFullPath.getAbsolutePath(), downloadedFile.get().getAbsolutePath());
            assertEquals("some other content", IOUtils.readFile(downloadedFile.get()));

            // Verify download status when downloaded
            assertDownloadStatus(fileDownloader, fileReference, 100.0);
        }
    }

    @Test
    public void setFilesToDownload() throws IOException {
        File downloadDir = Files.createTempDirectory("filedistribution").toFile();
        MockConnection configSource = new MockConnection();
        FileDownloader fileDownloader = new FileDownloader(configSource, downloadDir, Duration.ofMillis(200));
        FileReference foo = new FileReference("foo");
        FileReference bar = new FileReference("bar");
        List<FileReference> fileReferences = Arrays.asList(foo, bar);
        fileDownloader.download(fileReferences);

        assertEquals(fileReferences, fileDownloader.queuedForDownload().asList());

        // Verify download status
        assertDownloadStatus(fileDownloader, foo, 0.0);
        assertDownloadStatus(fileDownloader, bar, 0.0);
    }

    private void writeFileReference(File dir, String fileReferenceString, String fileName) throws IOException {
        File file = new File(new File(dir, fileReferenceString), fileName);
        IOUtils.writeFile(file, "content", false);
    }

    private File fileReferenceFullPath(File dir, String fileReferenceString) {
        return new File(dir, fileReferenceString);
    }

    private void assertDownloadStatus(FileDownloader fileDownloader, FileReference fileReference, double expectedDownloadStatus) {
        double downloadStatus = fileDownloader.downloadStatus(fileReference);
        assertEquals(expectedDownloadStatus, downloadStatus, 0.0001);
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
        public void invokeAsync(Request request, double jrtTimeout, RequestWaiter requestWaiter) {
            responseHandler.request(request);
        }

        @Override
        public void invokeSync(Request request, double jrtTimeout) {
            responseHandler.request(request);
        }

        @Override
        public void setError(int errorCode) {
        }

        @Override
        public void setSuccess() {
        }

        @Override
        public String getAddress() {
            return null;
        }

        @Override
        public void close() {
        }

        @Override
        public void setError(Connection connection, int errorCode) {
            connection.setError(errorCode);
        }

        @Override
        public Connection getCurrent() {
            return this;
        }

        @Override
        public Connection setNewCurrentConnection() {
            return this;
        }

        @Override
        public int getSize() {
            return 1;
        }

        public void setResponseHandler(ResponseHandler responseHandler) {
            this.responseHandler = responseHandler;
        }

        static class FileReferenceFoundResponseHandler implements ResponseHandler {

            @Override
            public void request(Request request) {
                if (request.methodName().equals("filedistribution.serveFile"))
                    request.returnValues().add(new Int32Value(0));
            }
        }

        static class UnknownFileReferenceResponseHandler implements ResponseHandler {

            @Override
            public void request(Request request) {
                if (request.methodName().equals("filedistribution.serveFile"))
                    request.returnValues().add(new Int32Value(1));
            }
        }

        public interface ResponseHandler {

            void request(Request request);

        }

    }

}
