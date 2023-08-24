// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy.filedistribution;

import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.jrt.Method;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Supervisor;
import com.yahoo.security.tls.Capability;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.defaults.Defaults;
import net.jpountz.xxhash.XXHashFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.vespa.config.UrlDownloader.DOES_NOT_EXIST;
import static com.yahoo.vespa.config.UrlDownloader.HTTP_ERROR;
import static com.yahoo.vespa.config.UrlDownloader.INTERNAL_ERROR;
import static java.lang.Runtime.getRuntime;
import static java.util.concurrent.Executors.newFixedThreadPool;

/**
 * An RPC server that handles URL download requests.
 *
 * @author lesters
 */
class UrlDownloadRpcServer {

    private static final Logger log = Logger.getLogger(UrlDownloadRpcServer.class.getName());
    private static final String CONTENTS_FILE_NAME = "contents";
    static final File defaultDownloadDirectory = new File(Defaults.getDefaults().underVespaHome("var/db/vespa/download"));

    private final File rootDownloadDir;
    private final ExecutorService executor = newFixedThreadPool(Math.max(8, getRuntime().availableProcessors()),
                                                                new DaemonThreadFactory("Rpc URL download executor"));

    UrlDownloadRpcServer(Supervisor supervisor) {
        this.rootDownloadDir = defaultDownloadDirectory;
        supervisor.addMethod(new Method("url.waitFor", "s", "s", this::download)
                                    .requireCapabilities(Capability.CONFIGPROXY__FILEDISTRIBUTION_API)
                                    .methodDesc("get path to url download")
                                    .paramDesc(0, "url", "url")
                                    .returnDesc(0, "path", "path to file"));
    }

    void close() {
        executor.shutdownNow();
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void download(Request req) {
        req.detach();
        executor.execute(() -> downloadFile(req));
    }

    private void downloadFile(Request req) {
        String url = req.parameters().get(0).asString();
        File downloadDir = new File(rootDownloadDir, urlToDirName(url));
        if (alreadyDownloaded(downloadDir)) {
            log.log(Level.INFO, "URL '" + url + "' already downloaded");
            req.returnValues().add(new StringValue(new File(downloadDir, CONTENTS_FILE_NAME).getAbsolutePath()));
            req.returnRequest();
            return;
        }

        try {
            URL website = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) website.openConnection();
            if (connection.getResponseCode() == 200) {
                log.log(Level.INFO, "Downloading URL '" + url + "'");
                downloadFile(req, connection, downloadDir);
            } else {
                log.log(Level.SEVERE, "Download of URL '" + url + "' got server response: " + connection.getResponseCode());
                req.setError(HTTP_ERROR, String.valueOf(connection.getResponseCode()));
            }
        } catch (Throwable e) {
            log.log(Level.SEVERE, "Download of URL '" + url + "' failed, got exception: " + e.getMessage());
            req.setError(INTERNAL_ERROR, "Download of URL '" + url + "' internal error: " + e.getMessage());
        }
        req.returnRequest();
    }

    private static void downloadFile(Request req, HttpURLConnection connection, File downloadDir) throws IOException {
        long start = System.currentTimeMillis();
        String url = connection.getURL().toString();
        Files.createDirectories(downloadDir.toPath());
        File contentsPath = new File(downloadDir, CONTENTS_FILE_NAME);
        try (ReadableByteChannel rbc = Channels.newChannel(connection.getInputStream())) {
            try (FileOutputStream fos = new FileOutputStream((contentsPath.getAbsolutePath()))) {
                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);

                if (contentsPath.exists() && contentsPath.length() > 0) {
                    new RequestTracker().trackRequest(downloadDir);
                    req.returnValues().add(new StringValue(contentsPath.getAbsolutePath()));
                    log.log(Level.FINE, () -> "URL '" + url + "' available at " + contentsPath);
                    log.log(Level.INFO, String.format("Download of URL '%s' done in %.3f seconds",
                                                      url, (System.currentTimeMillis() - start) / 1000.0));
                } else {
                    log.log(Level.SEVERE, "Downloaded URL '" + url + "' not found, returning error");
                    req.setError(DOES_NOT_EXIST, "Downloaded '" + url + "' not found");
                }
            }
        }
    }

    private static String urlToDirName(String uri) {
        return String.valueOf(XXHashFactory.fastestJavaInstance().hash64().hash(ByteBuffer.wrap(Utf8.toBytes(uri)), 0));
    }

    private static boolean alreadyDownloaded(File downloadDir) {
        File contents = new File(downloadDir, CONTENTS_FILE_NAME);
        return contents.exists() && contents.length() > 0;
    }

}
