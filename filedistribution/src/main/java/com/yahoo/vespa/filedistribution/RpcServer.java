// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.jrt.DoubleArray;
import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.Method;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.StringArray;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Supervisor;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.proxy.filedistribution.FileDownloader;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An RPC server that handles file distribution requests.
 *
 * @author hmusum
 */
public class RpcServer {

    private final static Logger log = Logger.getLogger(RpcServer.class.getName());

    private final Supervisor supervisor;
    private final FileDownloader downloader;

    public RpcServer(Supervisor supervisor, FileDownloader downloader) {
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
        supervisor.addMethod(new Method("filedistribution.receiveFile", "ssxlis", "i", // TODO Temporary method to get started with testing
                this, "receiveFile")
                .methodDesc("receive file reference content")
                .paramDesc(0, "file references", "file reference to download")
                .paramDesc(1, "filename", "filename")
                .paramDesc(2, "content", "array of bytes")
                .paramDesc(3, "hash", "xx64hash of the file content")
                .paramDesc(4, "errorcode", "Error code. 0 if none")
                .paramDesc(5, "error-description", "Error description.")
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
        FileReference fileReference = new FileReference(req.parameters().get(0).asString());
        log.log(LogLevel.DEBUG, "getFile() called for file reference '" + fileReference.value() + "'");
        Optional<File> pathToFile = downloader.getFile(fileReference);
        try {
            if (pathToFile.isPresent()) {
                req.returnValues().add(new StringValue(pathToFile.get().getAbsolutePath()));
                log.log(LogLevel.INFO, "File reference '" + fileReference.value() + "' available at " + pathToFile.get());
            } else {
                log.log(LogLevel.INFO, "File reference '" + fileReference.value() + "' not found, returning error");
                req.setError(fileReferenceDoesNotExists, "File reference '" + fileReference.value() + "' not found");
            }
        } catch (Throwable e) {
            log.log(LogLevel.WARNING, "File reference '" + fileReference.value() + "' got exeption: " + e.getMessage());
            req.setError(fileReferenceInternalError, "File reference '" + fileReference.value() + "' removed");
        }
        req.returnRequest();
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
        String[] fileReferenceStrings = req.parameters().get(0).asStringArray();
        List<FileReference> fileReferences = Stream.of(fileReferenceStrings)
                .map(FileReference::new)
                .collect(Collectors.toList());
        downloader.queueForDownload(fileReferences);

        req.returnValues().add(new Int32Value(0));
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public final void receiveFile(Request req) {
        FileReference fileReference = new FileReference(req.parameters().get(0).asString());
        String filename = req.parameters().get(1).asString();
        byte[] content = req.parameters().get(2).asData();
        long xxhash = req.parameters().get(3).asInt64();
        int errorCode = req.parameters().get(3).asInt32();
        String errorDescription = req.parameters().get(4).asString();

        if (errorCode == 0) {
            //downloader.receive(fileReference, filename, content);
            req.returnValues().add(new Int32Value(0));
        } else {
            log.log(LogLevel.WARNING, "Receiving file reference '" + fileReference.value() + "' failed: " + errorDescription);
            req.returnValues().add(new Int32Value(1));
            // TODO: Add error description return value here too?
        }
    }
}
