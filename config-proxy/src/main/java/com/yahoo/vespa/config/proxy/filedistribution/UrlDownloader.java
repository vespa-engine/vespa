// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy.filedistribution;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Download of urls
 *
 * @author hmusum
 */
class UrlDownloader {

    private static final Logger log = Logger.getLogger(UrlDownloader.class.getName());
    private static final String CONTENTS_FILE_NAME = "contents";
    private static final String USER_AGENT_MODEL_DOWNLOADER = "Vespa/8.x (model download - https://github.com/vespa-engine/vespa)";

    private final URL url;
    private final DownloadOptions downloadOptions;

    public UrlDownloader(URL url, DownloadOptions downloadOptions) {
        this.url = url;
        this.downloadOptions = downloadOptions;
    }

    public Optional<File> download(File downloadDir) throws IOException {
        long start = System.currentTimeMillis();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("User-Agent", USER_AGENT_MODEL_DOWNLOADER);

        downloadOptions.getAuthToken()
                .ifPresent(token -> connection.setRequestProperty("Authorization", "Bearer " + token));

        if (connection.getResponseCode() != 200)
            throw new RuntimeException("Download of URL '" + this.url + "' failed, got response code " + connection.getResponseCode());

        log.log(Level.INFO, "Downloading URL '" + this.url + "'");
        File contentsPath = new File(downloadDir, fileName());
        try (ReadableByteChannel rbc = Channels.newChannel(connection.getInputStream())) {
            try (FileOutputStream fos = new FileOutputStream((contentsPath.getAbsolutePath()))) {
                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);

                if (contentsPath.exists() && contentsPath.length() > 0) {
                    new RequestTracker().trackRequest(downloadDir);
                    log.log(Level.FINE, () -> "URL '" + this.url + "' available at " + contentsPath);
                    log.log(Level.INFO, String.format("Download of URL '%s' done in %.3f seconds",
                                                      this.url, (System.currentTimeMillis() - start) / 1000.0));
                    return Optional.of(contentsPath);
                } else {
                    log.log(Level.SEVERE, "Downloaded URL '" + this.url + "' not found, returning error");
                    return Optional.empty();
                }
            }
        }
    }

    public String fileName() {
        String path = url.getPath();
        var fileName = path.substring(path.lastIndexOf('/') + 1);
        return fileName.isEmpty() ? CONTENTS_FILE_NAME : fileName;
    }

    boolean alreadyDownloaded(File downloadDir) {
        File contents = new File(downloadDir,fileName());
        return contents.exists() && contents.length() > 0;
    }
}
