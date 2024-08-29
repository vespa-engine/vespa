// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy.filedistribution;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
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
    private static final String USER_AGENT_MODEL_DOWNLOADER = "Vespa/8.x (model download - https://github.com/vespa-engine/vespa)";

    @Override
    public Optional<File> downloadFile(String urlString, File downloadDir) throws IOException {
        long start = System.currentTimeMillis();
        URI uri = getUri(urlString);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setRequestProperty("User-Agent", USER_AGENT_MODEL_DOWNLOADER);
        if (connection.getResponseCode() != 200)
            throw new RuntimeException("Download of URL '" + uri + "' failed, got response code " + connection.getResponseCode());

        log.log(Level.INFO, "Downloading URL '" + uri + "'");
        File contentsPath = new File(downloadDir, filename(uri));
        try (ReadableByteChannel rbc = Channels.newChannel(connection.getInputStream())) {
            try (FileOutputStream fos = new FileOutputStream((contentsPath.getAbsolutePath()))) {
                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);

                if (contentsPath.exists() && contentsPath.length() > 0) {
                    new RequestTracker().trackRequest(downloadDir);
                    log.log(Level.FINE, () -> "URL '" + uri + "' available at " + contentsPath);
                    log.log(Level.INFO, String.format("Download of URL '%s' done in %.3f seconds",
                                                      uri, (System.currentTimeMillis() - start) / 1000.0));
                    return Optional.of(contentsPath);
                } else {
                    log.log(Level.SEVERE, "Downloaded URL '" + uri + "' not found, returning error");
                    return Optional.empty();
                }
            }
        }
    }

    private static URI getUri(String urlString) {
        URI uri;
        try {
            uri = new URI(urlString);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        return uri;
    }

    private String filename(URI uri) {
        String path = uri.getPath();
        var fileName = path.substring(path.lastIndexOf('/') + 1);
        return fileName.isEmpty() ? CONTENTS_FILE_NAME : fileName;
    }

}
