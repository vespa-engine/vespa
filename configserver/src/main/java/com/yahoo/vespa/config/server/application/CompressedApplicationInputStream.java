// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.compress.ArchiveStreamReader;
import com.yahoo.compress.ArchiveStreamReader.Options;
import com.yahoo.vespa.config.server.http.BadRequestException;
import com.yahoo.vespa.config.server.http.InternalServerException;
import com.yahoo.vespa.config.server.http.v2.ApplicationApiHandler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * A compressed application points to an application package that can be decompressed.
 *
 * @author Ulf Lilleengen
 */
public class CompressedApplicationInputStream implements AutoCloseable {

    private static final Logger log = Logger.getLogger(CompressedApplicationInputStream.class.getPackage().getName());

    private final ArchiveStreamReader reader;

    private CompressedApplicationInputStream(ArchiveStreamReader reader) {
        this.reader = reader;
    }

    /**
     * Create an instance of a compressed application from an input stream.
     *
     * @param is   the input stream containing the compressed files.
     * @param contentType the content type for determining what kind of compressed stream should be used.
     * @param maxSizeInBytes the maximum allowed size of the decompressed content
     * @return An instance of an unpacked application.
     */
    public static CompressedApplicationInputStream createFromCompressedStream(InputStream is, String contentType, long maxSizeInBytes) {
        try {
            Options options = Options.standard().maxSize(maxSizeInBytes).allowDotSegment(true);
            return switch (contentType) {
                case ApplicationApiHandler.APPLICATION_X_GZIP ->
                        new CompressedApplicationInputStream(ArchiveStreamReader.ofTarGzip(is, options));
                case ApplicationApiHandler.APPLICATION_ZIP ->
                        new CompressedApplicationInputStream(ArchiveStreamReader.ofZip(is, options));
                default -> throw new BadRequestException("Unable to decompress");
            };
        } catch (UncheckedIOException e) {
            throw new InternalServerException("Unable to create compressed application stream", e);
        }
    }

    /**
     * Close this stream.
     * @throws IOException if the stream could not be closed
     */
    public void close() throws IOException {
        reader.close();
    }

    File decompress() throws IOException {
        return decompress(uncheck(() -> java.nio.file.Files.createTempDirectory("decompress")).toFile());
    }

    public File decompress(File dir) throws IOException {
        decompressInto(dir.toPath());
        return dir;
    }

    private void decompressInto(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) throw new IllegalArgumentException("Not a directory: " + dir.toAbsolutePath());
        log.log(Level.FINE, () -> "Application is in " + dir.toAbsolutePath());
        int entries = 0;
        Path tmpFile = null;
        OutputStream tmpStream = null;
        try {
            tmpFile = createTempFile(dir);
            tmpStream = Files.newOutputStream(tmpFile);
            ArchiveStreamReader.ArchiveFile file;
            while ((file = reader.readNextTo(tmpStream)) != null) {
                tmpStream.close();
                log.log(Level.FINE, "Creating output file: " + file.path());
                Path dstFile = dir.resolve(file.path().toString()).normalize();
                Files.createDirectories(dstFile.getParent());
                Files.move(tmpFile, dstFile);
                tmpFile = createTempFile(dir);
                tmpStream = Files.newOutputStream(tmpFile);
                entries++;
            }
        } finally {
            if (tmpStream != null) tmpStream.close();
            if (tmpFile != null) Files.deleteIfExists(tmpFile);
        }
        if (entries == 0) {
            log.log(Level.WARNING, "Not able to decompress any entries to " + dir);
        }
    }

    private static Path createTempFile(Path applicationDir) throws IOException {
        return Files.createTempFile(applicationDir, "application", null);
    }

}
