// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy.filedistribution;

import com.yahoo.text.Text;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.util.Timeout;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
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
        var tempFile = Files.createTempFile(downloadDir.toPath(), fileName(), "inprogress");
        var target = downloadDir.toPath().resolve(fileName());
        HttpGet get = new HttpGet(uri);
        downloadOptions.getAuthToken()
                       .ifPresent(token -> get.setHeader(new BasicHeader("Authorization", "Bearer " + token)));
        return httpClient.execute(get, resp -> {
            var code = resp.getCode();
            if (code != 200)
                throw new RuntimeException("Download of URL '" + uri + "' failed, got response code " + code);
            return writeContent(downloadDir, resp, tempFile, target, start);
        });
    }

    private Optional<File> writeContent(File downloadDir, ClassicHttpResponse resp, Path tempFile, Path target, long start) throws IOException {
        InputStream content = resp.getEntity().getContent();
        if (content == null) return Optional.empty();

        try (var in = content) {
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            if (Files.exists(tempFile) && Files.size(tempFile) > 0) {
                Files.move(tempFile, target);
                new RequestTracker().trackRequest(downloadDir);
                log.log(Level.FINE, () -> "URL '" + uri + "' available at " + target);
                log.log(Level.INFO, Text.format("Download of URL '%s' done in %.3f seconds",
                                                  uri, (System.currentTimeMillis() - start) / 1000.0));
                return Optional.of(target.toFile());
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
                                .setRedirectStrategy(new StripAuthOnCrossOriginRedirectStrategy())
                                .setRetryStrategy(new DefaultHttpRequestRetryStrategy())
                                .setUserAgent(USER_AGENT_MODEL_DOWNLOADER)
                                .setDefaultRequestConfig(
                                        RequestConfig.custom()
                                                     .setConnectionRequestTimeout(Timeout.ofSeconds(30))
                                                     .setResponseTimeout(Timeout.ofSeconds(30))
                                                     .setMaxRedirects(5)
                                                     .build())
                                .build();
    }

    /**
     * Follows redirects like the default strategy, but never sends the auth token to another origin: the default
     * strategy refuses cross-origin redirects when an Authorization header is set (e.g. huggingface.co redirecting
     * to a CDN with a pre-signed url), so instead drop the header and follow the redirect without it.
     */
    private static class StripAuthOnCrossOriginRedirectStrategy extends DefaultRedirectStrategy {

        @Override
        public boolean isRedirectAllowed(HttpHost currentTarget, HttpHost newTarget, HttpRequest redirect, HttpContext context) {
            if ( ! super.isRedirectAllowed(currentTarget, newTarget, redirect, context))
                redirect.removeHeaders(HttpHeaders.AUTHORIZATION);
            return true;
        }

    }

}
