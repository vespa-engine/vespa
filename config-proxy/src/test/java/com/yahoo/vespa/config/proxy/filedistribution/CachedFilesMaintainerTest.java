// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy.filedistribution;

import com.yahoo.io.IOUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author hmusum
 */
public class CachedFilesMaintainerTest {

    private File cachedFileReferences;
    private File cachedDownloads;
    private CachedFilesMaintainer cachedFilesMaintainer;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void setup() throws IOException {
        cachedFileReferences = tempFolder.newFolder();
        cachedDownloads = tempFolder.newFolder();
        cachedFilesMaintainer = new CachedFilesMaintainer(cachedFileReferences, cachedDownloads, Duration.ofMinutes(1));
    }

    @Test
    public void require_old_files_to_be_deleted() throws IOException {
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

}
