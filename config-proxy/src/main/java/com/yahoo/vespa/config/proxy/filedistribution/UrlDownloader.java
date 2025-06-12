// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy.filedistribution;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.message.BasicHeader;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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

    private final URI uri;
    private final DownloadOptions downloadOptions;
    private final HttpClient httpClient = createClient();

    public UrlDownloader(URI uri, DownloadOptions downloadOptions) {
        this.uri = uri;
        this.downloadOptions = downloadOptions;
    }

    public Optional<File> download(File downloadDir) throws IOException {
        long start = System.currentTimeMillis();
        log.log(Level.INFO, "Downloading URL '" + uri + "'");
        File contentsPath = new File(downloadDir, fileName());
        HttpGet get = new HttpGet(uri);
        downloadOptions.getAuthToken()
                       .ifPresent(token -> get.setHeader(new BasicHeader("Authorization", "Bearer " + token)));
        return httpClient.execute(get, resp -> {
            var code = resp.getCode();
            if (code != 200)
                throw new RuntimeException("Download of URL '" + uri + "' failed, got response code " + code);
            return writeContent(downloadDir, resp, contentsPath, start);
        });
    }

    private Optional<File> writeContent(File downloadDir, ClassicHttpResponse resp, File contentsPath, long start) throws IOException {
        try (var in = resp.getEntity().getContent()) {
            Files.copy(in, contentsPath.toPath(), StandardCopyOption.REPLACE_EXISTING);
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

    public String fileName() {
        String path = uri.getPath();
        var fileName = path.substring(path.lastIndexOf('/') + 1);
        return fileName.isEmpty() ? CONTENTS_FILE_NAME : fileName;
    }

    boolean alreadyDownloaded(File downloadDir) {
        File contents = new File(downloadDir,fileName());
        return contents.exists() && contents.length() > 0;
    }

    private static HttpClient createClient() {
        return HttpClientBuilder.create()
                                .setRetryStrategy(new DefaultHttpRequestRetryStrategy())
                                .setUserAgent(USER_AGENT_MODEL_DOWNLOADER)
                                .build();
    }

}
