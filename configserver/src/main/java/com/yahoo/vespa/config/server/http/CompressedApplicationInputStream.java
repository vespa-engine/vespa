// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.server.http.v2.ApplicationApiHandler;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;

import java.io.*;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

/**
 * A compressed application points to an application package that can be decompressed.
 *
 * @author lulf
 * @since 5.1
 */
public class CompressedApplicationInputStream implements AutoCloseable {

    private static final Logger log = Logger.getLogger(CompressedApplicationInputStream.class.getPackage().getName());
    private final ArchiveInputStream ais;

    /**
     * Create an instance of a compressed application from an input stream.
     *
     * @param is   the input stream containing the compressed files.
     * @param contentType the content type for determining what kind of compressed stream should be used.
     * @return An instance of an unpacked application.
     */
    public static CompressedApplicationInputStream createFromCompressedStream(InputStream is, String contentType) {
        try {
            ArchiveInputStream ais = getArchiveInputStream(is, contentType);
            return createFromCompressedStream(ais);
        } catch (IOException e) {
            throw new InternalServerException("Unable to create compressed application stream", e);
        }
    }

    public static CompressedApplicationInputStream createFromCompressedStream(ArchiveInputStream ais) {
        return new CompressedApplicationInputStream(ais);
    }

    private static ArchiveInputStream getArchiveInputStream(InputStream is, String contentTypeHeader) throws IOException {
        ArchiveInputStream ais;
        switch (contentTypeHeader) {
            case ApplicationApiHandler.APPLICATION_X_GZIP:
                ais = new TarArchiveInputStream(new GZIPInputStream(is));
                break;
            case ApplicationApiHandler.APPLICATION_ZIP:
                ais = new ZipArchiveInputStream(is);
                break;
            default:
                throw new BadRequestException("Unable to decompress");
        }
        return ais;
    }

    private CompressedApplicationInputStream(ArchiveInputStream ais) {
        this.ais = ais;
    }

    /**
     * Close this stream.
     * @throws IOException if the stream could not be closed
     */
    public void close() throws IOException {
        ais.close();
    }

    File decompress() throws IOException {
        return decompress(Files.createTempDir());
    }

    public File decompress(File dir) throws IOException {
        decompressInto(dir);
        dir = findActualApplicationDir(dir);
        return dir;
    }

    private void decompressInto(File application) throws IOException {
        log.log(LogLevel.DEBUG, "Application is in " + application.getAbsolutePath());
        int entries = 0;
        ArchiveEntry entry;
        while ((entry = ais.getNextEntry()) != null) {
            log.log(LogLevel.DEBUG, "Unpacking " + entry.getName());
            File outFile = new File(application, entry.getName());
            // FIXME/TODO: write more tests that break this logic. I have a feeling it is not very robust.
            if (entry.isDirectory()) {
                if (!(outFile.exists() && outFile.isDirectory())) {
                    log.log(LogLevel.DEBUG, "Creating dir: " + outFile.getAbsolutePath());
                    boolean res = outFile.mkdirs();
                    if (!res) {
                        log.log(LogLevel.WARNING, "Could not create dir " + entry.getName());
                    }
                }
            } else {
                log.log(LogLevel.DEBUG, "Creating output file: " + outFile.getAbsolutePath());

                // Create parent dir if necessary
                String parent = outFile.getParent();
                new File(parent).mkdirs();

                FileOutputStream fos = new FileOutputStream(outFile);
                ByteStreams.copy(ais, fos);
                fos.close();
            }
            entries++;
        }
        if (entries == 0) {
            log.log(LogLevel.WARNING, "Not able to read any entries from " + application.getName());
        }
    }

    private File findActualApplicationDir(File application) {
        // If application is in e.g. application/, use that as root for UnpackedApplication
        File[] files = application.listFiles();
        if (files != null && files.length == 1 && files[0].isDirectory()) {
            application = files[0];
        }
        return application;
    }
}
