// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy.filedistribution;

import com.yahoo.io.IOUtils;
import com.yahoo.vespa.config.util.ConfigUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author hmusum
 */
public class FileReferencesAndDownloadsMaintainerTest {

    private static final Duration keepDuration = Duration.ofMinutes(1);
    private static final int outDatedFilesToKeep = 9;

    private File cachedFileReferences;
    private File cachedDownloads;
    private FileReferencesAndDownloadsMaintainer maintainer;

    @TempDir
    public File tempFolder;

    @BeforeEach
    public void setup() throws IOException {
        cachedFileReferences = newFolder(tempFolder, "cachedFileReferences");
        cachedDownloads = newFolder(tempFolder, "cachedDownloads");
    }

    @Test
    void require_old_files_to_be_deleted() {
        maintainer = new FileReferencesAndDownloadsMaintainer(cachedFileReferences, cachedDownloads, keepDuration, outDatedFilesToKeep,
                                                              List.of("host1"));
        runMaintainerAndAssertFiles(0, 0);

        var fileReferences = writeFiles(20);
        var downloads = writeDownloads(21);
        runMaintainerAndAssertFiles(20, 21);

        updateLastModifiedTimestamp(0, 5, fileReferences, downloads);
        runMaintainerAndAssertFiles(15, 16);

        updateLastModifiedTimestamp(6, 20, fileReferences, downloads);
        // Should keep at least outDatedFilesToKeep file references and downloads even if there are more that are old
        runMaintainerAndAssertFiles(outDatedFilesToKeep, outDatedFilesToKeep);
    }

    @Test
    void require_no_files_deleted_when_running_on_config_server_host() {
        maintainer = new FileReferencesAndDownloadsMaintainer(cachedFileReferences, cachedDownloads, keepDuration,
                                                              outDatedFilesToKeep, List.of(ConfigUtils.getCanonicalHostName()));
        runMaintainerAndAssertFiles(0, 0);

        var fileReferences = writeFiles(10);
        var downloads = writeDownloads(10);
        runMaintainerAndAssertFiles(10, 10);

        updateLastModifiedTimestamp(0, 10, fileReferences, downloads);
        runMaintainerAndAssertFiles(10, 10);
    }

    private void updateLastModifiedTimestamp(int startInclusive, int endExclusive, List<File> fileReferences, List<File> downloads) {
        IntStream.range(startInclusive, endExclusive).forEach(i -> {
            Instant instant = Instant.now().minus(keepDuration.plus(Duration.ofMinutes(1)).minus(Duration.ofSeconds(i)));
            updateLastModifiedTimeStamp(fileReferences.get(i), instant);
            updateLastModifiedTimeStamp(downloads.get(i), instant);
        });
    }

    private List<File> writeFiles(int count) {
        List<File> files = new ArrayList<>();
        IntStream.range(0, count).forEach(i -> {
            try {
                files.add(writeFile(cachedFileReferences, "fileReference" + i));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        return files;
    }

    private List<File> writeDownloads(int count) {
        List<File> files = new ArrayList<>();
        IntStream.range(0, count).forEach(i -> {
            try {
                files.add(writeFile(cachedDownloads, "download" + i));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        return files;
    }

    private void updateLastModifiedTimeStamp(File file, Instant instant) {
        if (!file.setLastModified(instant.toEpochMilli())) {
            throw new RuntimeException("Could not set last modified timestamp for '" + file.getAbsolutePath() + "'");
        }
    }

    private void runMaintainerAndAssertFiles(int fileReferenceCount, int downloadCount) {
        maintainer.run();
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
