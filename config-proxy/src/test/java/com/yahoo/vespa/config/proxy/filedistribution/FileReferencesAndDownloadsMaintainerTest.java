// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy.filedistribution;

import com.yahoo.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author hmusum
 */
public class FileReferencesAndDownloadsMaintainerTest {

    private File cachedFileReferences;
    private File cachedDownloads;
    private FileReferencesAndDownloadsMaintainer cachedFilesMaintainer;

    @TempDir
    public File tempFolder;

    @BeforeEach
    public void setup() throws IOException {
        cachedFileReferences = newFolder(tempFolder, "cachedFileReferences");
        cachedDownloads = newFolder(tempFolder, "cachedDownloads");
        cachedFilesMaintainer = new FileReferencesAndDownloadsMaintainer(cachedFileReferences, cachedDownloads, Duration.ofMinutes(1));
    }

    @Test
    void require_old_files_to_be_deleted() throws IOException {
        runMaintainerAndAssertFiles(0, 0);

        File fileReference = writeFile(cachedFileReferences, "fileReference");
        File download = writeFile(cachedDownloads, "download");
        runMaintainerAndAssertFiles(1, 1);

        updateLastModifiedTimeStamp(fileReference, Instant.now().minus(Duration.ofMinutes(10)));
        runMaintainerAndAssertFiles(0, 1);

        updateLastModifiedTimeStamp(download, Instant.now().minus(Duration.ofMinutes(10)));
        runMaintainerAndAssertFiles(0, 0);
    }

    private void updateLastModifiedTimeStamp(File file, Instant instant) {
        if (!file.setLastModified(instant.toEpochMilli())) {
            throw new RuntimeException("Could not set last modified timestamp for '" + file.getAbsolutePath() + "'");
        }
    }

    private void runMaintainerAndAssertFiles(int fileReferenceCount, int downloadCount) {
        cachedFilesMaintainer.run();
        File[] fileReferences = cachedFileReferences.listFiles();
        assertNotNull(fileReferences);
        assertEquals(fileReferenceCount, fileReferences.length);

        File[] downloads = cachedDownloads.listFiles();
        assertNotNull(downloads);
        assertEquals(downloadCount, downloads.length);
    }

    private File writeFile(File directory, String filename) throws IOException {
        File file = new File(directory, filename);
        IOUtils.writeFile(file, filename, false);
        return file;
    }

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
        }
        return result;
    }

}
