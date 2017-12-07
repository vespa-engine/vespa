// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.Method;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Supervisor;
import com.yahoo.log.LogLevel;
import net.jpountz.xxhash.XXHash64;
import net.jpountz.xxhash.XXHashFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class FileReceiver {

    private final static Logger log = Logger.getLogger(FileReceiver.class.getName());
    private final static String RECEIVE_METHOD = "filedistribution.receiveFile";
    private final static String RECEIVE_META_METHOD = "filedistribution.receiveFileMeta";
    private final static String RECEIVE_PART_METHOD = "filedistribution.receiveFilePart";
    private final static String RECEIVE_EOF_METHOD = "filedistribution.receiveFileEof";

    private final Supervisor supervisor;
    private final FileReferenceDownloader downloader;
    private final File downloadDirectory;
    private final XXHash64 hasher = XXHashFactory.fastestInstance().hash64();

    FileReceiver(Supervisor supervisor, FileReferenceDownloader downloader, File downloadDirectory) {
        this.supervisor = supervisor;
        this.downloader = downloader;
        this.downloadDirectory = downloadDirectory;
        registerMethods();
    }

    private void registerMethods() {
        receiveFileMethod(this).forEach(supervisor::addMethod);
    }

    // Defined here so that it can be added to supervisor used by client (server will use same connection when calling
    // receiveFile after getting a serveFile method call). handler needs to implement receiveFile method
    private List<Method> receiveFileMethod(Object handler) {
        List<Method> methods = new ArrayList<>();
        methods.add(new Method(RECEIVE_META_METHOD, "ssl", "ii", handler,"receiveFileMeta")
                .paramDesc(0, "filereference", "file reference to download")
                .paramDesc(1, "filename", "filename")
                .paramDesc(2, "filelength", "length in bytes of file")
                .returnDesc(0, "ret", "0 if success, 1 otherwise")
                .returnDesc(1, "session-id", "Session id to be used for this transfer"));
        methods.add(new Method(RECEIVE_PART_METHOD, "siix", "i", handler,"receiveFilePart")
                .paramDesc(0, "filereference", "file reference to download")
                .paramDesc(1, "session-id", "Session id to be used for this transfer")
                .paramDesc(2, "partid", "relative part number starting at zero")
                .paramDesc(3, "data", "bytes in this part")
                .returnDesc(0, "ret", "0 if success, 1 otherwise"));
        methods.add(new Method(RECEIVE_EOF_METHOD, "silis", "i", handler,"receiveFileEof")
                .paramDesc(0, "filereference", "file reference to download")
                .paramDesc(1, "session-id", "Session id to be used for this transfer")
                .paramDesc(2, "crc-code", "crc code (xxhash64)")
                .paramDesc(3, "error-code", "Error code. 0 if none")
                .paramDesc(4, "error-description", "Error description.")
                .returnDesc(0, "ret", "0 if success, 1 if crc mismatch, 2 otherwise"));
        // Temporary method until we have chunking
        methods.add(new Method(RECEIVE_METHOD, "sssxlis", "i", handler, "receiveFile")
                .methodDesc("receive file reference content")
                .paramDesc(0, "file reference", "file reference to download")
                .paramDesc(1, "filename", "filename")
                .paramDesc(2, "type", "'file' or 'compressed'")
                .paramDesc(3, "content", "array of bytes")
                .paramDesc(4, "hash", "xx64hash of the file content")
                .paramDesc(5, "errorcode", "Error code. 0 if none")
                .paramDesc(6, "error-description", "Error description.")
                .returnDesc(0, "ret", "0 if success, 1 otherwise"));
        return methods;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public final void receiveFile(Request req) {
        FileReference fileReference = new FileReference(req.parameters().get(0).asString());
        String filename = req.parameters().get(1).asString();
        String type = req.parameters().get(2).asString();
        byte[] content = req.parameters().get(3).asData();
        long xxhash = req.parameters().get(4).asInt64();
        int errorCode = req.parameters().get(5).asInt32();
        String errorDescription = req.parameters().get(6).asString();

        if (errorCode == 0) {
            // TODO: Remove when system test works
            log.log(LogLevel.INFO, "Receiving file reference '" + fileReference.value() + "'");
            receiveFile(new FileReferenceData(fileReference, filename, FileReferenceData.Type.valueOf(type), content, xxhash));
            req.returnValues().add(new Int32Value(0));
        } else {
            log.log(LogLevel.WARNING, "Receiving file reference '" + fileReference.value() + "' failed: " + errorDescription);
            req.returnValues().add(new Int32Value(1));
            // TODO: Add error description return value here too?
        }
    }

    void receiveFile(FileReferenceData fileReferenceData) {
        long xxHashFromContent = hasher.hash(ByteBuffer.wrap(fileReferenceData.content()), 0);
        if (xxHashFromContent != fileReferenceData.xxhash())
            throw new RuntimeException("xxhash from content (" + xxHashFromContent + ") is not equal to xxhash in request (" + fileReferenceData.xxhash()+ ")");

        File fileReferenceDir = new File(downloadDirectory, fileReferenceData.fileReference().value());
        // file might be a directory (and then type is compressed)
        File file = new File(fileReferenceDir, fileReferenceData.filename());
        try {
            File tempFile = new File(Files.createTempDirectory("downloaded").toFile(), fileReferenceData.filename());
            Files.write(tempFile.toPath(), fileReferenceData.content());

            // Unpack if necessary
            if (fileReferenceData.type() == FileReferenceData.Type.compressed) {
                File decompressedDir = Files.createTempDirectory("decompressed").toFile();
                log.log(LogLevel.DEBUG, "Compressed file, unpacking " + tempFile + " to " + decompressedDir);
                CompressedFileReference.decompress(tempFile, decompressedDir);
                moveFileToDestination(decompressedDir, fileReferenceDir);
            } else {
                log.log(LogLevel.DEBUG, "Uncompressed file, moving to " + file.getAbsolutePath());
                Files.createDirectories(fileReferenceDir.toPath());
                moveFileToDestination(tempFile, file);
            }
            downloader.completedDownloading(fileReferenceData.fileReference(), file);
        } catch (IOException e) {
            log.log(LogLevel.ERROR, "Failed writing file: " + e.getMessage(), e);
            throw new RuntimeException("Failed writing file: ", e);
        }
    }

    private void moveFileToDestination(File tempFile, File destination) {
        try {
            Files.move(tempFile.toPath(), destination.toPath());
            log.log(LogLevel.INFO, "File moved from " + tempFile.getAbsolutePath()+ " to " + destination.getAbsolutePath());
        } catch (FileAlreadyExistsException e) {
            // Don't fail if it already exists (we might get the file from several config servers when retrying, servers are down etc.
            // so it might be written already)
            log.log(LogLevel.INFO, "File '" + destination.getAbsolutePath() + "' already exists, continuing: " + e.getMessage());
        } catch (IOException e) {
            String message = "Failed moving file '" + tempFile.getAbsolutePath() + "' to '" + destination.getAbsolutePath() + "'";
            log.log(LogLevel.ERROR, message, e);
            throw new RuntimeException(message, e);
        }
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public final void receiveFileMeta(Request req) {
        log.info("Received method call '" + req.methodName() + "' with parameters : " + req.parameters());
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public final void receiveFilePart(Request req) {
        log.info("Received method call '" + req.methodName() + "' with parameters : " + req.parameters());
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public final void receiveFileEof(Request req) {
        log.info("Received method call '" + req.methodName() + "' with parameters : " + req.parameters());
    }
}
