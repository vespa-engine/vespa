// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy.filedistribution;

import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.jrt.Method;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Supervisor;
import com.yahoo.net.URI;
import com.yahoo.security.tls.Capability;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.yolean.Exceptions;
import net.jpountz.xxhash.XXHashFactory;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.vespa.config.UrlDownloader.DOES_NOT_EXIST;
import static com.yahoo.vespa.config.UrlDownloader.HTTP_ERROR;
import static com.yahoo.vespa.config.UrlDownloader.INTERNAL_ERROR;
import static java.lang.Runtime.getRuntime;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.logging.Level.WARNING;

/**
 * An RPC server that handles URL download requests.
 *
 * @author lesters
 */
class UrlDownloadRpcServer {

    private static final Logger log = Logger.getLogger(UrlDownloadRpcServer.class.getName());
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
            if ( ! executor.awaitTermination(10, TimeUnit.SECONDS))
                log.log(WARNING, "Failed to shut down url download rpc server within timeout");
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
        Downloader downloader = downloader(url);
        if (downloader.alreadyDownloaded(downloader, downloadDir)) {
            log.log(Level.INFO, "URL '" + url + "' already downloaded");
            req.returnValues().add(new StringValue(new File(downloadDir, downloader.fileName()).getAbsolutePath()));
            req.returnRequest();
            return;
        }

        try {
            Files.createDirectories(downloadDir.toPath());
            Optional<File> file = downloader.downloadFile(url, downloadDir);
            if (file.isPresent())
                req.returnValues().add(new StringValue(file.get().getAbsolutePath()));
            else
                req.setError(DOES_NOT_EXIST, "URL '" + url + "' not found");
        } catch (RuntimeException e) {
            logAndSetRpcError(req, url, e, HTTP_ERROR);
        } catch (Throwable e) {
            logAndSetRpcError(req, url, e, INTERNAL_ERROR);
        }
        req.returnRequest();
    }

    private static Downloader downloader(String url) {
        URI uri = new URI(url);
        return switch (uri.getScheme()) {
            case "http", "https" -> new UrlDownloader();
            case "s3" -> new S3Downloader();
            default -> throw new IllegalArgumentException("Unsupported scheme '" + uri.getScheme() + "'");
        };
    }

    private static void logAndSetRpcError(Request req, String url, Throwable e, int rpcErrorCode) {
        String message = "Download of '" + url + "' failed: " + Exceptions.toMessageString(e);
        log.log(Level.SEVERE, message);
        req.setError(rpcErrorCode, e.getMessage());
    }

    private static String urlToDirName(String uri) {
        return String.valueOf(XXHashFactory.fastestJavaInstance().hash64().hash(ByteBuffer.wrap(Utf8.toBytes(uri)), 0));
    }

}
