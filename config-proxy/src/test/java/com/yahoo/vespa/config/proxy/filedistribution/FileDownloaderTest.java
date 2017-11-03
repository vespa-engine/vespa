package com.yahoo.vespa.config.proxy.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.io.IOUtils;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FileDownloaderTest {
    private static final ConfigSourceSet configSourceSet = new ConfigSourceSet();

    @Test
    public void download() throws IOException {
        File downloadDir = Files.createTempDirectory("filedistribution").toFile();
        FileDownloader fileDownloader = new FileDownloader(configSourceSet, downloadDir.getAbsolutePath(), Duration.ofMillis(200));

        // Write a file to download directory to simulate download going OK
        String fileReferenceString = "somehash";
        String fileName = "foo.jar";
        File fileReferenceFullPath = fileReferenceFullPath(downloadDir, fileReferenceString);
        FileReference fileReference = writeFileReference(downloadDir, fileReferenceString, fileName);

        // Check that we get correct path and content when asking for file reference
        Optional<File> pathToFile = fileDownloader.getFile(fileReference);
        assertTrue(pathToFile.isPresent());
        String downloadedFile = new File(fileReferenceFullPath, fileName).getAbsolutePath();
        assertEquals(new File(fileReferenceFullPath, fileName).getAbsolutePath(), downloadedFile);
        assertEquals("content", IOUtils.readFile(pathToFile.get()));

        // Verify download status
        Map<FileReference, Double> downloadStatus = fileDownloader.downloadStatus();
        assertEquals(1, downloadStatus.size());
        assertDownloadStatus(Collections.singletonList(fileReference), downloadStatus.entrySet().iterator().next(), 100.0);

        // Non-existing file
        assertFalse(fileReferenceFullPath.getAbsolutePath(), fileDownloader.getFile(new FileReference("doesnotexist")).isPresent());
    }

    @Test
    public void setFilesToDownload() throws IOException {
        File downloadDir = Files.createTempDirectory("filedistribution").toFile();
        FileDownloader fileDownloader = new FileDownloader(configSourceSet, downloadDir.getAbsolutePath(), Duration.ofMillis(200));
        List<FileReference> fileReferences = Arrays.asList(new FileReference("foo"), new FileReference("bar"));
        fileDownloader.queueForDownload(fileReferences);

        assertEquals(fileReferences, fileDownloader.queuedForDownload().asList());

        // Verify download status
        Map<FileReference, Double> downloadStatus = fileDownloader.downloadStatus();
        assertEquals(2, downloadStatus.size());

        assertDownloadStatus(fileReferences, downloadStatus.entrySet().iterator().next(), 0.0);
        assertDownloadStatus(fileReferences, downloadStatus.entrySet().iterator().next(), 0.0);
    }

    private FileReference writeFileReference(File dir, String fileReferenceString, String fileName) throws IOException {
        File file = new File(new File(dir, fileReferenceString), fileName);
        IOUtils.writeFile(file, "content", false);
        return new FileReference(fileReferenceString);
    }

    private File fileReferenceFullPath(File dir, String fileReferenceString) {
        return new File(dir, fileReferenceString);
    }

    private void assertDownloadStatus(List<FileReference> fileReferences, Map.Entry<FileReference, Double> entry, double expectedDownloadStatus) {
        assertTrue(fileReferences.contains(new FileReference(entry.getKey().value())));
        assertEquals(expectedDownloadStatus, entry.getValue(), 0.0001);
    }
}
