// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.io.IOUtils;
import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.Method;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Supervisor;
import com.yahoo.security.tls.Capability;
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
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.vespa.filedistribution.FileReferenceData.Type;
import static com.yahoo.vespa.filedistribution.FileReferenceData.CompressionType;

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
    private final Downloads downloads;
    private final File downloadDirectory;
    private final AtomicInteger nextSessionId = new AtomicInteger(1);
    private final Map<Integer, Session> sessions = new HashMap<>();

    final static class Session {
        private final StreamingXXHash64 hasher;
        private final int sessionId;
        private final FileReference reference;
        private final Type fileType;
        private final CompressionType compressionType;
        private final String fileName;
        private final long fileSize;
        private long currentFileSize;
        private long currentPartId;
        private final long currentHash;
        private final File fileReferenceDir;
        private final File tmpDir;
        private final File inprogressFile;

        Session(File downloadDirectory,
                int sessionId,
                FileReference reference,
                Type fileType,
                FileReferenceData.CompressionType compressionType,
                String fileName,
                long fileSize) {
            this.hasher = XXHashFactory.fastestInstance().newStreamingHash64(0);
            this.sessionId = sessionId;
            this.reference = reference;
            this.fileType = fileType;
            this.compressionType = compressionType;
            this.fileName = fileName;
            this.fileSize = fileSize;
            currentFileSize = 0;
            currentPartId = 0;
            currentHash = 0;
            fileReferenceDir = new File(downloadDirectory, reference.value());
            this.tmpDir = downloadDirectory;

            try {
                inprogressFile = Files.createTempFile(tmpDir.toPath(), fileName, ".inprogress").toFile();
            } catch (IOException e) {
                String msg = "Failed creating temp file for inprogress file for " + fileName + " in '" + tmpDir.toPath() + "': ";
                log.log(Level.SEVERE, msg + e.getMessage(), e);
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
                String message = "Failed writing to file (" + inprogressFile.toPath() + "): ";
                log.log(Level.SEVERE, message + e.getMessage(), e);
                boolean successfulDelete = inprogressFile.delete();
                if ( ! successfulDelete)
                    log.log(Level.INFO, "Unable to delete " + inprogressFile.toPath());
                throw new RuntimeException(message, e);
            }
            currentFileSize += part.length;
            currentPartId++;
            hasher.update(part, 0, part.length);
        }

        File close(long hash) {
            verifyHash(hash);

            File file = new File(fileReferenceDir, fileName);
            File decompressedDir = null;
            try {
                if (fileType == Type.file) {
                    try {
                        Files.createDirectories(fileReferenceDir.toPath());
                    } catch (IOException e) {
                        log.log(Level.SEVERE, "Failed creating directory (" + fileReferenceDir.toPath() + "): " + e.getMessage(), e);
                        throw new RuntimeException("Failed creating directory (" + fileReferenceDir.toPath() + "): ", e);
                    }
                    log.log(Level.FINE, () -> "Uncompressed file, moving to " + file.getAbsolutePath());
                    moveFileToDestination(inprogressFile, file);
                } else {
                    decompressedDir = Files.createTempDirectory(tmpDir.toPath(), "archive").toFile();
                    log.log(Level.FINE, () -> "compression type to use=" + compressionType);
                    new FileReferenceCompressor(fileType, compressionType).decompress(inprogressFile, decompressedDir);
                    moveFileToDestination(decompressedDir, fileReferenceDir);
                }
            } catch (IOException e) {
                log.log(Level.SEVERE, "Failed writing file: " + e.getMessage(), e);
                throw new RuntimeException("Failed writing file: ", e);
            } finally {
                deletePath(inprogressFile);
                deletePath(decompressedDir);
            }
            return file;
        }

        double percentageReceived() {
            return (double)currentFileSize/(double)fileSize;
        }

        void verifyHash(long hash) {
            if (hasher.getValue() != hash)
                throw new RuntimeException("xxhash from content (" + currentHash + ") is not equal to xxhash in request (" + hash + ")");
        }

    }

    FileReceiver(Supervisor supervisor, Downloads downloads, File downloadDirectory) {
        this.supervisor = supervisor;
        this.downloads = downloads;
        this.downloadDirectory = downloadDirectory;
        registerMethods();
    }

    private void registerMethods() {
        receiveFileMethod().forEach(supervisor::addMethod);
    }

    // Defined here so that it can be added to supervisor used by client (server will use same connection when calling
    // receiveFile after getting a serveFile method call). handler needs to implement receiveFile* methods
    private List<Method> receiveFileMethod() {
        List<Method> methods = new ArrayList<>();
        methods.add(new Method(RECEIVE_META_METHOD, "sssl*", "ii", this::receiveFileMeta)
                .requireCapabilities(Capability.CLIENT__FILERECEIVER_API)
                .paramDesc(0, "filereference", "file reference to download")
                .paramDesc(1, "filename", "filename")
                .paramDesc(2, "type", "'file' or 'compressed'")
                .paramDesc(3, "filelength", "length in bytes of file")
                .paramDesc(3, "compressionType", "compression type: gzip, lz4, zstd")
                .returnDesc(0, "ret", "0 if success, 1 otherwise")
                .returnDesc(1, "session-id", "Session id to be used for this transfer"));
        methods.add(new Method(RECEIVE_PART_METHOD, "siix", "i", this::receiveFilePart)
                .requireCapabilities(Capability.CLIENT__FILERECEIVER_API)
                .paramDesc(0, "filereference", "file reference to download")
                .paramDesc(1, "session-id", "Session id to be used for this transfer")
                .paramDesc(2, "partid", "relative part number starting at zero")
                .paramDesc(3, "data", "bytes in this part")
                .returnDesc(0, "ret", "0 if success, 1 otherwise"));
        methods.add(new Method(RECEIVE_EOF_METHOD, "silis", "i", this::receiveFileEof)
                .requireCapabilities(Capability.CLIENT__FILERECEIVER_API)
                .paramDesc(0, "filereference", "file reference to download")
                .paramDesc(1, "session-id", "Session id to be used for this transfer")
                .paramDesc(2, "crc-code", "crc code (xxhash64)")
                .paramDesc(3, "error-code", "Error code. 0 if none")
                .paramDesc(4, "error-description", "Error description.")
                .returnDesc(0, "ret", "0 if success, 1 if crc mismatch, 2 otherwise"));
        return methods;
    }

    private static void moveFileToDestination(File tempFile, File destination) {
        try {
            Files.move(tempFile.toPath(), destination.toPath());
            log.log(Level.FINEST, () -> "File moved from " + tempFile.getAbsolutePath()+ " to " + destination.getAbsolutePath());
        } catch (FileAlreadyExistsException e) {
            // Don't fail if it already exists (we might get the file from several config servers when retrying, servers are down etc.
            // so it might be written already). Delete temp file/dir in that case, to avoid filling the disk.
            log.log(Level.FINE, () -> "Failed moving file '" + tempFile.getAbsolutePath() + "' to '" +
                    destination.getAbsolutePath() + "', it already exists");
        } catch (IOException e) {
            String message = "Failed moving file '" + tempFile.getAbsolutePath() + "' to '" + destination.getAbsolutePath() + "'";
            log.log(Level.SEVERE, message, e);
            throw new RuntimeException(message, e);
        } finally {
            deletePath(tempFile);
        }
    }

    private static void deletePath(File path) {
        if (path == null) return;
        if ( ! path.exists()) return;

        try {
            if (path.isDirectory())
                IOUtils.recursiveDeleteDir(path);
            else
                Files.delete(path.toPath());
        } catch (IOException ioe) {
            log.log(Level.WARNING, "Failed deleting file/dir " + path);
        }
    }

    private void receiveFileMeta(Request req) {
        log.log(Level.FINE, () -> "Received method call '" + req.methodName() + "' with parameters : " + req.parameters());
        FileReference reference = new FileReference(req.parameters().get(0).asString());
        String fileName = req.parameters().get(1).asString();
        Type type = FileReferenceData.Type.valueOf(req.parameters().get(2).asString());
        long fileSize = req.parameters().get(3).asInt64();
        CompressionType compressionType = (req.parameters().size() > 4)
                ? CompressionType.valueOf(req.parameters().get(4).asString())
                : CompressionType.gzip; // fallback/legacy compression type
        int sessionId = nextSessionId.getAndIncrement();
        int retval = 0;
        synchronized (sessions) {
            if (sessions.containsKey(sessionId)) {
                retval = 1;
                log.severe("Session id " + sessionId + " already exist, impossible. Request from(" + req.target() + ")");
            } else {
                try {
                    sessions.put(sessionId, new Session(downloadDirectory, sessionId, reference,
                                                        type, compressionType, fileName, fileSize));
                } catch (Exception e) {
                    retval = 1;
                }
            }
        }
        req.returnValues().add(new Int32Value(retval));
        req.returnValues().add(new Int32Value(sessionId));
    }

    private void receiveFilePart(Request req) {
        log.log(Level.FINEST, () -> "Received method call '" + req.methodName() + "' with parameters : " + req.parameters());

        FileReference reference = new FileReference(req.parameters().get(0).asString());
        int sessionId = req.parameters().get(1).asInt32();
        int partId = req.parameters().get(2).asInt32();
        byte [] part = req.parameters().get(3).asData();
        Session session = getSession(sessionId);
        int retval = verifySession(session, sessionId, reference);
        if (retval == 0) {
            try {
                session.addPart(partId, part);
            } catch (Exception e) {
                log.severe("Got exception " + e);
                retval = 1;
            }
            double completeness = (double) session.currentFileSize / (double) session.fileSize;
            log.log(Level.FINEST, () -> String.format("%.1f percent of '%s' downloaded", completeness * 100, reference.value()));
            downloads.setDownloadStatus(reference, completeness);
        }
        req.returnValues().add(new Int32Value(retval));
    }

    private void receiveFileEof(Request req) {
        log.log(Level.FINE, () -> "Received method call '" + req.methodName() + "' with parameters : " + req.parameters());
        FileReference reference = new FileReference(req.parameters().get(0).asString());
        int sessionId = req.parameters().get(1).asInt32();
        long xxhash = req.parameters().get(2).asInt64();
        Session session = getSession(sessionId);
        int retval = verifySession(session, sessionId, reference);
        File file = session.close(xxhash);
        downloads.completedDownloading(reference, file);
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
