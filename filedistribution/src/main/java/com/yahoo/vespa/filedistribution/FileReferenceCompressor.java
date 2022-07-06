// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.filedistribution;

import com.google.common.io.ByteStreams;
import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Utility class for compressing and decompressing files used in a file reference
 *
 * @author hmusum
 */
public class FileReferenceCompressor {

    private static final Logger log = Logger.getLogger(FileReferenceCompressor.class.getName());
    private static final int recurseDepth = 100;

    private final FileReferenceData.Type type;
    private final FileReferenceData.CompressionType compressionType;

    public FileReferenceCompressor(FileReferenceData.Type type, FileReferenceData.CompressionType compressionType) {
        this.type = Objects.requireNonNull(type, "Type cannot be null");
        this.compressionType = Objects.requireNonNull(compressionType, "Compression type cannot be null");
    }

    public File compress(File baseDir, List<File> inputFiles, File outputFile) throws IOException {
        TarArchiveOutputStream archiveOutputStream = new TarArchiveOutputStream(compressedOutputStream(outputFile));
        archiveOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
        createArchiveFile(archiveOutputStream, baseDir, inputFiles);
        return outputFile;
    }

    public File compress(File directory, File outputFile) throws IOException {
        return compress(directory,
                        Files.find(Paths.get(directory.getAbsolutePath()),
                                   recurseDepth,
                                   (p, basicFileAttributes) -> basicFileAttributes.isRegularFile())
                             .map(Path::toFile).collect(Collectors.toList()),
                        outputFile);
    }

    public void decompress(File inputFile, File outputDir) throws IOException {
        log.log(Level.FINE, () -> "Decompressing '" + inputFile + "' into '" + outputDir + "'");
        try (ArchiveInputStream ais = new TarArchiveInputStream(decompressedInputStream(inputFile))) {
            decompress(ais, outputDir);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Unable to decompress '" + inputFile.getAbsolutePath() + "': " + e.getMessage());
        }
    }

    private static void decompress(ArchiveInputStream archiveInputStream, File outputFile) throws IOException {
        int entries = 0;
        ArchiveEntry entry;
        while ((entry = archiveInputStream.getNextEntry()) != null) {
            File outFile = new File(outputFile, entry.getName());
            if (entry.isDirectory()) {
                if (!(outFile.exists() && outFile.isDirectory())) {
                    log.log(Level.FINE, () -> "Creating dir: " + outFile.getAbsolutePath());
                    if (!outFile.mkdirs()) {
                        log.log(Level.WARNING, "Could not create dir " + entry.getName());
                    }
                }
            } else {
                // Create parent dir if necessary
                File parent = new File(outFile.getParent());
                if (!parent.exists() && !parent.mkdirs()) {
                    log.log(Level.WARNING, "Could not create dir " + parent.getAbsolutePath());
                }
                FileOutputStream fos = new FileOutputStream(outFile);
                ByteStreams.copy(archiveInputStream, fos);
                fos.close();
            }
            entries++;
        }
        if (entries == 0) {
            throw new IllegalArgumentException("Not able to read any entries from stream (" +
                                                       archiveInputStream.getBytesRead() + " bytes read from stream)");
        }
    }

    private static void createArchiveFile(ArchiveOutputStream archiveOutputStream, File baseDir, List<File> inputFiles) throws IOException {
        inputFiles.forEach(file -> {
            try {
                writeFileToTar(archiveOutputStream, baseDir, file);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        archiveOutputStream.close();
    }

    private static void writeFileToTar(ArchiveOutputStream taos, File baseDir, File file) throws IOException {
        log.log(Level.FINEST, () -> "Adding file to tar: " + baseDir.toPath().relativize(file.toPath()).toString());
        taos.putArchiveEntry(taos.createArchiveEntry(file, baseDir.toPath().relativize(file.toPath()).toString()));
        ByteStreams.copy(new FileInputStream(file), taos);
        taos.closeArchiveEntry();
    }

    private OutputStream compressedOutputStream(File outputFile) throws IOException {
        switch (type) {
            case compressed:
                log.log(Level.FINE, () -> "Compressing with compression type " + compressionType);
                switch (compressionType) {
                    case gzip:
                        return new GZIPOutputStream(new FileOutputStream(outputFile));
                    case lz4:
                        return new LZ4BlockOutputStream(new FileOutputStream(outputFile));
                    default:
                        throw new RuntimeException("Unknown compression type " + compressionType);
                }
            case file:
                return new FileOutputStream(outputFile);
            default:
                throw new RuntimeException("Unknown file reference type " + type);
        }
    }

    private InputStream decompressedInputStream(File inputFile) throws IOException {
        switch (type) {
            case compressed:
                log.log(Level.FINE, () -> "Decompressing with compression type " + compressionType);
                switch (compressionType) {
                    case gzip:
                        return new GZIPInputStream(new FileInputStream(inputFile));
                    case lz4:
                        return new LZ4BlockInputStream(new FileInputStream(inputFile));
                    default:
                        throw new RuntimeException("Unknown compression type " + compressionType);
                }
            case file:
                return new FileInputStream(inputFile);
            default:
                throw new RuntimeException("Unknown file reference type " + type);
        }
    }

}

