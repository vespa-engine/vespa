package com.yahoo.vespa.config.proxy.filedistribution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UrlDownloaderTest {

    @TempDir
    public File temporaryFolder;

    @Test
    public void testFileDownload() throws IOException {
        URI uri = URI.create("https://docs.vespa.ai/foo");
        var downloader = new UrlDownloader(uri, new DownloadOptions(null));

        assertFalse(downloader.alreadyDownloaded(temporaryFolder));

        Files.write(temporaryFolder.toPath().resolve("foo"), "bar".getBytes());
        assertTrue(downloader.alreadyDownloaded(temporaryFolder));
    }
}
