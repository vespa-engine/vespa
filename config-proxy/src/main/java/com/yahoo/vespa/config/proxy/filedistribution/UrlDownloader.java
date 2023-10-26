// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy.filedistribution;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Download of urls
 *
 * @author hmusum
 */
class UrlDownloader implements Downloader {

    private static final Logger log = Logger.getLogger(UrlDownloader.class.getName());
    private static final String CONTENTS_FILE_NAME = "contents";

    @Override
    public Optional<File> downloadFile(String url, File downloadDir) throws IOException {
        long start = System.currentTimeMillis();
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        if (connection.getResponseCode() != 200)
            throw new RuntimeException("Download of URL '" + url + "' failed, got response code " + connection.getResponseCode());

        log.log(Level.INFO, "Downloading URL '" + url + "'");
        File contentsPath = new File(downloadDir, CONTENTS_FILE_NAME);
        try (ReadableByteChannel rbc = Channels.newChannel(connection.getInputStream())) {
            try (FileOutputStream fos = new FileOutputStream((contentsPath.getAbsolutePath()))) {
                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);

                if (contentsPath.exists() && contentsPath.length() > 0) {
                    new RequestTracker().trackRequest(downloadDir);
                    log.log(Level.FINE, () -> "URL '" + url + "' available at " + contentsPath);
                    log.log(Level.INFO, String.format("Download of URL '%s' done in %.3f seconds",
                                                      url, (System.currentTimeMillis() - start) / 1000.0));
                    return Optional.of(contentsPath);
                } else {
                    log.log(Level.SEVERE, "Downloaded URL '" + url + "' not found, returning error");
                    return Optional.empty();
                }
            }
        }
    }

}
