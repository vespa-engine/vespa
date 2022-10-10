// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy.filedistribution;

import com.yahoo.io.IOUtils;
import com.yahoo.test.ManualClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author hmusum
 */
public class CachedFilesMaintainerTest {

    private static final int numberToAlwaysKeep = 2;

    private File cachedFileReferences;
    private File cachedDownloads;
    private CachedFilesMaintainer cachedFilesMaintainer;
    private final ManualClock clock = new ManualClock();

    @TempDir
    public File tempFolder;

    @BeforeEach
    public void setup() throws IOException {
        cachedFileReferences = newFolder(tempFolder, "cachedFileReferences");
        cachedDownloads = newFolder(tempFolder, "cachedDownloads");
        cachedFilesMaintainer = new CachedFilesMaintainer(cachedFileReferences,
                                                          cachedDownloads,
                                                          Duration.ofMinutes(2),
                                                          clock,
                                                          numberToAlwaysKeep);
    }

    @Test
    void require_old_files_to_be_deleted() {
        runMaintainerAndAssertFiles(0, 0);

        clock.advance(Duration.ofSeconds(55));
        // Create file references and downloads
        createFiles();

        runMaintainerAndAssertFiles(4, 4);

        clock.advance(Duration.ofMinutes(1));
        runMaintainerAndAssertFiles(3, 3);

        clock.advance(Duration.ofMinutes(100));
        runMaintainerAndAssertFiles(numberToAlwaysKeep, numberToAlwaysKeep);
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

    private void writeFileAndSetLastAccessedTime(File directory, String filename) throws IOException {
        File file = new File(directory, filename);
        IOUtils.writeFile(file, filename, false);
        Files.setAttribute(file.toPath(), "lastAccessTime", FileTime.from(clock.instant()));
    }

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
        }
        return result;
    }

    private void createFiles() {
        IntStream.of(0,1,2,3).forEach(i -> {
            try {
                writeFileAndSetLastAccessedTime(cachedFileReferences, "fileReference" + i);
                writeFileAndSetLastAccessedTime(cachedDownloads, "download" + i);
                clock.advance(Duration.ofMinutes(1));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

}
