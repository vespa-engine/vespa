//  Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.config.proxy.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.Method;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Supervisor;
import com.yahoo.log.LogLevel;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Logger;

public class FileReceiver {

    private final static Logger log = Logger.getLogger(FileReceiver.class.getName());

    private final Supervisor supervisor;
    private final FileReferenceDownloader downloader;
    private final File downloadDirectory;

    public FileReceiver(Supervisor supervisor, FileReferenceDownloader downloader, File downloadDirectory) {
        this.supervisor = supervisor;
        this.downloader = downloader;
        this.downloadDirectory = downloadDirectory;
        registerMethods();
    }

    private void registerMethods() {
        supervisor.addMethod(receiveFileMethod(this));
    }

    // Defined here so that it can be added to supervisor used by client (server will use same connection when calling
    // receiveFile after getting a serveFile method call). handler needs to implement receiveFile method
    private Method receiveFileMethod(Object handler) {
        return new Method("filedistribution.receiveFile", "ssxlis", "i", // TODO Temporary method to get started with testing
                          handler, "receiveFile")
                .methodDesc("receive file reference content")
                .paramDesc(0, "file references", "file reference to download")
                .paramDesc(1, "filename", "filename")
                .paramDesc(2, "content", "array of bytes")
                .paramDesc(3, "hash", "xx64hash of the file content")
                .paramDesc(4, "errorcode", "Error code. 0 if none")
                .paramDesc(5, "error-description", "Error description.")
                .returnDesc(0, "ret", "0 if success, 1 otherwise");
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public final void receiveFile(Request req) {
        FileReference fileReference = new FileReference(req.parameters().get(0).asString());
        String filename = req.parameters().get(1).asString();
        byte[] content = req.parameters().get(2).asData();
        long xxhash = req.parameters().get(3).asInt64();
        int errorCode = req.parameters().get(4).asInt32();
        String errorDescription = req.parameters().get(5).asString();

        if (errorCode == 0) {
            // TODO: Remove when system test works
            log.log(LogLevel.INFO, "Receiving file reference '" + fileReference.value() + "'");
            receiveFile(fileReference, filename, content);
            req.returnValues().add(new Int32Value(0));
        } else {
            log.log(LogLevel.WARNING, "Receiving file reference '" + fileReference.value() + "' failed: " + errorDescription);
            req.returnValues().add(new Int32Value(1));
            // TODO: Add error description return value here too?
        }
    }

    void receiveFile(FileReference fileReference, String filename, byte[] content) {
        File fileReferenceDir = new File(downloadDirectory, fileReference.value());
        try {
            Files.createDirectories(fileReferenceDir.toPath());
            File file = new File(fileReferenceDir, filename);
            log.log(LogLevel.INFO, "Writing data to " + file.getAbsolutePath());
            Files.write(file.toPath(), content);
            downloader.completedDownloading(fileReference, file);
        } catch (IOException e) {
            log.log(LogLevel.ERROR, "Failed writing file: " + e.getMessage());
            throw new RuntimeException("Failed writing file: ", e);
        }
    }


}
