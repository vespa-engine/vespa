// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy.filedistribution;

import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.FileReference;
import com.yahoo.jrt.DoubleArray;
import com.yahoo.jrt.Method;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.StringArray;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Supervisor;
import com.yahoo.net.HostName;
import com.yahoo.security.tls.Capability;
import com.yahoo.vespa.filedistribution.FileDownloader;
import com.yahoo.vespa.filedistribution.FileReferenceDownload;

import java.io.File;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An RPC server that handles file distribution requests.
 *
 * @author hmusum
 */
class FileDistributionRpcServer {

    private final static Logger log = Logger.getLogger(FileDistributionRpcServer.class.getName());

    private final Supervisor supervisor;
    private final FileDownloader downloader;
    private final ExecutorService rpcDownloadExecutor = Executors.newFixedThreadPool(Math.max(8, Runtime.getRuntime().availableProcessors()),
                                                                                     new DaemonThreadFactory("Rpc executor"));

    FileDistributionRpcServer(Supervisor supervisor, FileDownloader downloader) {
        this.supervisor = supervisor;
        this.downloader = downloader;
        declareMethods();
    }

    void close() {
        rpcDownloadExecutor.shutdownNow();
        try {
            rpcDownloadExecutor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void declareMethods() {
        // Legacy method, needs to be the same name as used in filedistributor
        supervisor.addMethod(new Method("waitFor", "s", "s", this::getFile)
                                     .requireCapabilities(Capability.CONFIGPROXY__FILEDISTRIBUTION_API)
                                     .methodDesc("get path to file reference")
                                     .paramDesc(0, "file reference", "file reference")
                                     .returnDesc(0, "path", "path to file"));
        supervisor.addMethod(new Method("filedistribution.getFile", "s", "s", this::getFile)
                                     .requireCapabilities(Capability.CONFIGPROXY__FILEDISTRIBUTION_API)
                                     .methodDesc("get path to file reference")
                                     .paramDesc(0, "file reference", "file reference")
                                     .returnDesc(0, "path", "path to file"));
        supervisor.addMethod(new Method("filedistribution.getActiveFileReferencesStatus", "", "SD", this::getActiveFileReferencesStatus)
                                     .requireCapabilities(Capability.CONFIGPROXY__FILEDISTRIBUTION_API)
                                     .methodDesc("download status for file references")
                                     .returnDesc(0, "file references", "array of file references")
                                     .returnDesc(1, "download status", "percentage downloaded of each file reference in above array"));
    }


    //---------------- RPC methods ------------------------------------
    // TODO: Duplicate of code in FileAcquirerImpl. Find out where to put it. What about C++ code using this RPC call?
    private static final int baseErrorCode = 0x10000;
    private static final int baseFileProviderErrorCode = baseErrorCode + 0x1000;

    private static final int fileReferenceDoesNotExists = baseFileProviderErrorCode;

    private void getFile(Request req) {
        req.detach();
        rpcDownloadExecutor.execute(() -> downloadFile(req));
    }

    private void getActiveFileReferencesStatus(Request req) {
        Map<FileReference, Double> downloadStatus = downloader.downloadStatus();

        String[] fileRefArray = new String[downloadStatus.keySet().size()];
        fileRefArray = downloadStatus.keySet().stream()
                .map(FileReference::value)
                .toList()
                .toArray(fileRefArray);

        double[] downloadStatusArray = new double[downloadStatus.values().size()];
        int i = 0;
        for (Double d : downloadStatus.values()) {
            downloadStatusArray[i++] = d;
        }

        req.returnValues().add(new StringArray(fileRefArray));
        req.returnValues().add(new DoubleArray(downloadStatusArray));
    }

    private void downloadFile(Request req) {
        FileReference fileReference = new FileReference(req.parameters().get(0).asString());
        log.log(Level.FINE, () -> "getFile() called for file reference '" + fileReference.value() + "'");
        Optional<File> file = downloader.getFile(new FileReferenceDownload(fileReference, HostName.getLocalhost()));
        if (file.isPresent()) {
            new RequestTracker().trackRequest(file.get().getParentFile());
            req.returnValues().add(new StringValue(file.get().getAbsolutePath()));
            log.log(Level.FINE, () -> "File reference '" + fileReference.value() + "' available at " + file.get());
        } else {
            log.log(Level.INFO, "File reference '" + fileReference.value() + "' not found, returning error");
            req.setError(fileReferenceDoesNotExists, "File reference '" + fileReference.value() + "' not found");
        }

        req.returnRequest();
    }

}
