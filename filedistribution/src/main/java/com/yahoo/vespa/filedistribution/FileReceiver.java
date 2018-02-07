// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.Method;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Supervisor;
import com.yahoo.log.LogLevel;
import net.jpountz.xxhash.StreamingXXHash64;
import net.jpountz.xxhash.XXHashFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * When asking for a file reference, this handles RPC callbacks from config server with file data and metadata.
 * Uses the same Supervisor as the original caller that requests files, so communication uses the same
 * connection in both directions.
 *
 * @author baldersheim
 */
public class FileReceiver {

    private final static Logger log = Logger.getLogger(FileReceiver.class.getName());
    public final static String RECEIVE_META_METHOD = "filedistribution.receiveFileMeta";
    public final static String RECEIVE_PART_METHOD = "filedistribution.receiveFilePart";
    public final static String RECEIVE_EOF_METHOD = "filedistribution.receiveFileEof";

    private final Supervisor supervisor;
    private final FileReferenceDownloader downloader;
    private final File downloadDirectory;
    // Should be on same partition as downloadDirectory to make sure moving files from tmpDirectory
    // to downloadDirectory is atomic
    private final File tmpDirectory;
    private final AtomicInteger nextSessionId = new AtomicInteger(1);
    private final Map<Integer, Session> sessions = new HashMap<>();

    final static class Session {
        private final StreamingXXHash64 hasher;
        private final int sessionId;
        private final FileReference reference;
        private final FileReferenceData.Type fileType;
        private final String fileName;
        private final long fileSize;
        private long currentFileSize;
        private long currentPartId;
        private long currentHash;
        private final File fileReferenceDir;
        private final File tmpDir;
        private final File inprogressFile;

        Session(File downloadDirectory, File tmpDirectory, int sessionId, FileReference reference,
                FileReferenceData.Type fileType, String fileName, long fileSize)
        {
            this.hasher = XXHashFactory.fastestInstance().newStreamingHash64(0);
            this.sessionId = sessionId;
            this.reference = reference;
            this.fileType = fileType;
            this.fileName = fileName;
            this.fileSize = fileSize;
            currentFileSize = 0;
            currentPartId = 0;
            currentHash = 0;
            fileReferenceDir = new File(downloadDirectory, reference.value());
            this.tmpDir = tmpDirectory;

            try {
                inprogressFile = Files.createTempFile(tmpDirectory.toPath(), fileName, ".inprogress").toFile();
            } catch (IOException e) {
                String msg = "Failed creating temp file for inprogress file for " + fileName + " in '" + tmpDirectory.toPath() + "': ";
                log.log(LogLevel.ERROR, msg + e.getMessage(), e);
                throw new RuntimeException(msg, e);
            }
        }

        void addPart(int partId, byte [] part) {
            if (partId != currentPartId) {
                throw new IllegalStateException("Received partid " + partId + " while expecting " + currentPartId);
            }
            if (fileSize < currentFileSize + part.length) {
                throw new IllegalStateException("Received part would extend the file from " + currentFileSize + " to " +
                                                (currentFileSize + part.length) + ", but " + fileSize + " is max.");
            }
            try {
                Files.write(inprogressFile.toPath(), part, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                log.log(LogLevel.ERROR, "Failed writing to file(" + inprogressFile.toPath() + "): " + e.getMessage(), e);
                inprogressFile.delete();
                throw new RuntimeException("Failed writing to file(" + inprogressFile.toPath() + "): ", e);
            }
            currentFileSize += part.length;
            currentPartId++;
            hasher.update(part, 0, part.length);
        }

        File close(long hash) {
            if (hasher.getValue() != hash) {
                throw new RuntimeException("xxhash from content (" + currentHash + ") is not equal to xxhash in request (" + hash + ")");
            }
            File file = new File(fileReferenceDir, fileName);
            try {
                // Unpack if necessary
                if (fileType == FileReferenceData.Type.compressed) {
                    File decompressedDir = Files.createTempDirectory(tmpDir.toPath(), "archive").toFile();
                    log.log(LogLevel.DEBUG, "Archived file, unpacking " + inprogressFile + " to " + decompressedDir);
                    CompressedFileReference.decompress(inprogressFile, decompressedDir);
                    moveFileToDestination(decompressedDir, fileReferenceDir);
                } else {
                    try {
                        Files.createDirectories(fileReferenceDir.toPath());
                    } catch (IOException e) {
                        log.log(LogLevel.ERROR, "Failed creating directory (" + fileReferenceDir.toPath() + "): " + e.getMessage(), e);
                        throw new RuntimeException("Failed creating directory (" + fileReferenceDir.toPath() + "): ", e);
                    }
                    log.log(LogLevel.DEBUG, "Uncompressed file, moving to " + file.getAbsolutePath());
                    moveFileToDestination(inprogressFile, file);
                }
            } catch (IOException e) {
                log.log(LogLevel.ERROR, "Failed writing file: " + e.getMessage(), e);
                throw new RuntimeException("Failed writing file: ", e);
            } finally {
                try {
                    Files.delete(inprogressFile.toPath());
                } catch (IOException e) {
                    log.log(LogLevel.ERROR, "Failed deleting " + inprogressFile.getAbsolutePath() + ": " + e.getMessage(), e);
                }
            }
            return file;
        }

        double percentageReceived() {
            return (double)currentFileSize/(double)fileSize;
        }
    }

    FileReceiver(Supervisor supervisor, FileReferenceDownloader downloader, File downloadDirectory, File tmpDirectory) {
        this.supervisor = supervisor;
        this.downloader = downloader;
        this.downloadDirectory = downloadDirectory;
        this.tmpDirectory = tmpDirectory;
        registerMethods();
    }

    private void registerMethods() {
        receiveFileMethod(this).forEach(supervisor::addMethod);
    }

    // Defined here so that it can be added to supervisor used by client (server will use same connection when calling
    // receiveFile after getting a serveFile method call). handler needs to implement receiveFile* methods
    private List<Method> receiveFileMethod(Object handler) {
        List<Method> methods = new ArrayList<>();
        methods.add(new Method(RECEIVE_META_METHOD, "sssl", "ii", handler,"receiveFileMeta")
                .paramDesc(0, "filereference", "file reference to download")
                .paramDesc(1, "filename", "filename")
                .paramDesc(2, "type", "'file' or 'compressed'")
                .paramDesc(3, "filelength", "length in bytes of file")
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
        return methods;
    }

    void receiveFile(FileReferenceData fileReferenceData) {
        long xxHashFromContent = fileReferenceData.xxhash();
        if (xxHashFromContent != fileReferenceData.xxhash()) {
            throw new RuntimeException("xxhash from content (" + xxHashFromContent + ") is not equal to xxhash in request (" + fileReferenceData.xxhash() + ")");
        }

        File fileReferenceDir = new File(downloadDirectory, fileReferenceData.fileReference().value());
        // file might be a directory (and then type is compressed)
        File file = new File(fileReferenceDir, fileReferenceData.filename());
        try {
            File tempDownloadedDir = Files.createTempDirectory(tmpDirectory.toPath(), "downloaded").toFile();
            File tempFile = new File(tempDownloadedDir, fileReferenceData.filename());
            Files.write(tempFile.toPath(), fileReferenceData.content().array());

            // Unpack if necessary
            if (fileReferenceData.type() == FileReferenceData.Type.compressed) {
                File decompressedDir = Files.createTempDirectory(tempDownloadedDir.toPath(), "decompressed").toFile();
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

    private static void moveFileToDestination(File tempFile, File destination) {
        try {
            Files.move(tempFile.toPath(), destination.toPath());
            log.log(LogLevel.DEBUG, "File moved from " + tempFile.getAbsolutePath()+ " to " + destination.getAbsolutePath());
        } catch (FileAlreadyExistsException e) {
            // Don't fail if it already exists (we might get the file from several config servers when retrying, servers are down etc.
            // so it might be written already). Delete temp file in that case, to avoid filling the disk.
            log.log(LogLevel.DEBUG, "File '" + destination.getAbsolutePath() + "' already exists, continuing: " + e.getMessage());
            try {
                Files.delete(tempFile.toPath());
            } catch (IOException ioe) { /* ignore failure */}
        } catch (IOException e) {
            String message = "Failed moving file '" + tempFile.getAbsolutePath() + "' to '" + destination.getAbsolutePath() + "'";
            log.log(LogLevel.ERROR, message, e);
            throw new RuntimeException(message, e);
        }
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public final void receiveFileMeta(Request req) {
        log.log(LogLevel.DEBUG, "Received method call '" + req.methodName() + "' with parameters : " + req.parameters());
        FileReference reference = new FileReference(req.parameters().get(0).asString());
        String fileName = req.parameters().get(1).asString();
        String type = req.parameters().get(2).asString();
        long fileSize = req.parameters().get(3).asInt64();
        int sessionId = nextSessionId.getAndIncrement();
        int retval = 0;
        synchronized (sessions) {
            if (sessions.containsKey(sessionId)) {
                retval = 1;
                log.severe("Session id " + sessionId + " already exist, impossible. Request from(" + req.target() + ")");
            } else {
                try {
                    sessions.put(sessionId, new Session(downloadDirectory, tmpDirectory, sessionId, reference,
                                                        FileReferenceData.Type.valueOf(type),fileName, fileSize));
                } catch (Exception e) {
                    retval = 1;
                }
            }
        }
        req.returnValues().add(new Int32Value(retval));
        req.returnValues().add(new Int32Value(sessionId));
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public final void receiveFilePart(Request req) {
        log.log(LogLevel.DEBUG, "Received method call '" + req.methodName() + "' with parameters : " + req.parameters());

        FileReference reference = new FileReference(req.parameters().get(0).asString());
        int sessionId = req.parameters().get(1).asInt32();
        int partId = req.parameters().get(2).asInt32();
        byte [] part = req.parameters().get(3).asData();
        Session session = getSession(sessionId);
        int retval = verifySession(session, sessionId, reference);
        try {
            session.addPart(partId, part);
        } catch (Exception e) {
            log.severe("Got exception " + e);
            retval = 1;
        }
        double completeness = (double) session.currentFileSize / (double) session.fileSize;
        log.log(LogLevel.DEBUG, String.format("%.1f percent of '%s' downloaded", completeness * 100, reference.value()));
        downloader.setDownloadStatus(reference, completeness);
        req.returnValues().add(new Int32Value(retval));
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public final void receiveFileEof(Request req) {
        log.log(LogLevel.DEBUG, "Received method call '" + req.methodName() + "' with parameters : " + req.parameters());
        FileReference reference = new FileReference(req.parameters().get(0).asString());
        int sessionId = req.parameters().get(1).asInt32();
        long xxhash = req.parameters().get(2).asInt64();
        Session session = getSession(sessionId);
        int retval = verifySession(session, sessionId, reference);
        File file = session.close(xxhash);
        downloader.completedDownloading(reference, file);
        synchronized (sessions) {
            sessions.remove(sessionId);
        }
        req.returnValues().add(new Int32Value(retval));
    }

    private Session getSession(Integer sessionId) {
        synchronized (sessions) {
            return sessions.get(sessionId);
        }
    }
    private static int verifySession(Session session, int sessionId, FileReference reference) {
        if (session == null) {
            log.severe("session-id " + sessionId + " does not exist.");
            return 1;
        }
        if (! session.reference.equals(reference)) {
            log.severe("Session " + session.sessionId + " expects reference " + reference.value() + ", but was " + session.reference.value());
            return 1;
        }
        return 0;
    }
}
