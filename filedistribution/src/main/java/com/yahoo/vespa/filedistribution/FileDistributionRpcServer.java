// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.filedistribution;

import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.FileReference;
import com.yahoo.jrt.DoubleArray;
import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.Method;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.StringArray;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Supervisor;
import com.yahoo.log.LogLevel;

import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * An RPC server that handles file distribution requests.
 *
 * @author hmusum
 */
public class FileDistributionRpcServer {

    private final static Logger log = Logger.getLogger(FileDistributionRpcServer.class.getName());

    private final Supervisor supervisor;
    private final FileDownloader downloader;
    private final ExecutorService rpcDownloadExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(),
                                                                                     new DaemonThreadFactory("Rpc executor"));

    public FileDistributionRpcServer(Supervisor supervisor, FileDownloader downloader) {
        this.supervisor = supervisor;
        this.downloader = downloader;
        declareFileDistributionMethods();
    }

    private void declareFileDistributionMethods() {
        // Legacy method, needs to be the same name as used in filedistributor
        supervisor.addMethod(new Method("waitFor", "s", "s",
                                        this, "getFile")
                                     .methodDesc("get path to file reference")
                                     .paramDesc(0, "file reference", "file reference")
                                     .returnDesc(0, "path", "path to file"));
        supervisor.addMethod(new Method("filedistribution.getFile", "s", "s",
                                        this, "getFile")
                                     .methodDesc("get path to file reference")
                                     .paramDesc(0, "file reference", "file reference")
                                     .returnDesc(0, "path", "path to file"));
        supervisor.addMethod(new Method("filedistribution.getActiveFileReferencesStatus", "", "SD",
                                        this, "getActiveFileReferencesStatus")
                                     .methodDesc("download status for file references")
                                     .returnDesc(0, "file references", "array of file references")
                                     .returnDesc(1, "download status", "percentage downloaded of each file reference in above array"));
        supervisor.addMethod(new Method("filedistribution.setFileReferencesToDownload", "S", "i",
                                        this, "setFileReferencesToDownload")
                                     .methodDesc("set which file references to download")
                                     .paramDesc(0, "file references", "file reference to download")
                                     .returnDesc(0, "ret", "0 if success, 1 otherwise"));
    }


    //---------------- RPC methods ------------------------------------
    // TODO: Duplicate of code in FileAcquirereImpl. Find out where to put it. What about C++ code using this RPC call?
    private static final int baseErrorCode = 0x10000;
    private static final int baseFileProviderErrorCode = baseErrorCode + 0x1000;

    private static final int fileReferenceDoesNotExists = baseFileProviderErrorCode;
    private static final int fileReferenceRemoved = fileReferenceDoesNotExists + 1;
    private static final int fileReferenceInternalError = fileReferenceRemoved + 1;

    @SuppressWarnings({"UnusedDeclaration"})
    public final void getFile(Request req) {
        req.detach();
        rpcDownloadExecutor.execute(() -> downloadFile(req));
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public final void getActiveFileReferencesStatus(Request req) {
        Map<FileReference, Double> downloadStatus = downloader.downloadStatus();

        String[] fileRefArray = new String[downloadStatus.keySet().size()];
        fileRefArray = downloadStatus.keySet().stream()
                .map(FileReference::value)
                .collect(Collectors.toList())
                .toArray(fileRefArray);

        double[] downloadStatusArray = new double[downloadStatus.values().size()];
        int i = 0;
        for (Double d : downloadStatus.values()) {
            downloadStatusArray[i++] = d;
        }

        req.returnValues().add(new StringArray(fileRefArray));
        req.returnValues().add(new DoubleArray(downloadStatusArray));
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public final void setFileReferencesToDownload(Request req) {
        Arrays.stream(req.parameters().get(0).asStringArray())
                .map(FileReference::new)
                .forEach(fileReference -> downloader.download(new FileReferenceDownload(fileReference)));
        req.returnValues().add(new Int32Value(0));
    }

    private void downloadFile(Request req) {
        FileReference fileReference = new FileReference(req.parameters().get(0).asString());
        log.log(LogLevel.DEBUG, () -> "getFile() called for file reference '" + fileReference.value() + "'");
        Optional<File> pathToFile = downloader.getFile(fileReference);
        try {
            if (pathToFile.isPresent()) {
                req.returnValues().add(new StringValue(pathToFile.get().getAbsolutePath()));
                log.log(LogLevel.DEBUG, () -> "File reference '" + fileReference.value() + "' available at " + pathToFile.get());
            } else {
                log.log(LogLevel.INFO, "File reference '" + fileReference.value() + "' not found, returning error");
                req.setError(fileReferenceDoesNotExists, "File reference '" + fileReference.value() + "' not found");
            }
        } catch (Throwable e) {
            log.log(LogLevel.WARNING, "File reference '" + fileReference.value() + "' got exception: " + e.getMessage());
            req.setError(fileReferenceInternalError, "File reference '" + fileReference.value() + "' removed");
        }
        req.returnRequest();
    }

}
