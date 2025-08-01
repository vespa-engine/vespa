// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.filedistribution;

import io.airlift.compress.zstd.ZstdInputStream;
import com.yahoo.compress.ZstdOutputStream;
import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;
import org.apache.commons.compress.archivers.ArchiveEntry;
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
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
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
        try (var paths = Files.find(Path.of(directory.getAbsolutePath()), recurseDepth,
                                    (p, basicFileAttributes) -> basicFileAttributes.isRegularFile()))
        {
            return compress(directory, paths.map(Path::toFile).toList(), outputFile);
        }
    }

    public void decompress(File inputFile, File outputDir) throws IOException {
        log.log(Level.FINEST, () -> "Decompressing '" + inputFile + "' into '" + outputDir + "'");
        try (TarArchiveInputStream ais = new TarArchiveInputStream(decompressedInputStream(inputFile))) {
            decompress(ais, outputDir);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Unable to decompress '" + inputFile.getAbsolutePath() + "': " + e.getMessage());
        }
    }

    private static void decompress(TarArchiveInputStream archiveInputStream, File outputFile) throws IOException {
        int entries = 0;
        ArchiveEntry entry;
        while ((entry = archiveInputStream.getNextEntry()) != null) {
            File outFile = new File(outputFile, entry.getName());
            File canonicalOutFile = outFile.getCanonicalFile();
            File canonicalOutputDir = outputFile.getCanonicalFile();

            if (!canonicalOutFile.toPath().startsWith(canonicalOutputDir.toPath())) {
                throw new IOException("Invalid archive entry: " + entry.getName());
            }

            if (entry.isDirectory()) {
                if (!(canonicalOutFile.exists() && canonicalOutFile.isDirectory())) {
                    log.log(Level.FINE, () -> "Creating dir: " + canonicalOutFile.getAbsolutePath());
                    if (!canonicalOutFile.mkdirs()) {
                        log.log(Level.WARNING, "Could not create dir " + entry.getName());
                    }
                }
            } else {
                // Create parent dir if necessary
                File parent = new File(canonicalOutFile.getParent());
                if (!parent.exists() && !parent.mkdirs()) {
                    log.log(Level.WARNING, "Could not create dir " + parent.getAbsolutePath());
                }
                try (FileOutputStream fos = new FileOutputStream(canonicalOutFile)) {
                    archiveInputStream.transferTo(fos);
                }
            }
            entries++;
        }
        if (entries == 0) {
            throw new IllegalArgumentException("Not able to read any entries from stream (" +
                                                       archiveInputStream.getBytesRead() + " bytes read from stream)");
        }
    }

    private static <T extends ArchiveEntry> void createArchiveFile(ArchiveOutputStream<T> archiveOutputStream, File baseDir, List<File> inputFiles) throws IOException {
        inputFiles.forEach(file -> {
            try {
                writeFileToTar(archiveOutputStream, baseDir, file);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        archiveOutputStream.close();
    }

    private static <T extends ArchiveEntry> void writeFileToTar(ArchiveOutputStream<T> taos, File baseDir, File file) throws IOException {
        taos.putArchiveEntry(taos.createArchiveEntry(file, baseDir.toPath().relativize(file.toPath()).toString()));
        try (FileInputStream inputStream = new FileInputStream(file)) {
            inputStream.transferTo(taos);
        }
        taos.closeArchiveEntry();
    }

    private OutputStream compressedOutputStream(File outputFile) throws IOException {
        return switch (type) {
            case compressed -> switch (compressionType) {
                case gzip -> new GZIPOutputStream(new FileOutputStream(outputFile));
                case lz4 -> new LZ4BlockOutputStream(new FileOutputStream(outputFile));
                case none -> new FileOutputStream(outputFile);
                case zstd -> new ZstdOutputStream(new FileOutputStream(outputFile));
            };
            case file -> new FileOutputStream(outputFile);
        };
    }

    private InputStream decompressedInputStream(File inputFile) throws IOException {
        return switch (type) {
            case compressed -> switch (compressionType) {
                case gzip -> new GZIPInputStream(new FileInputStream(inputFile));
                case lz4 -> new LZ4BlockInputStream(new FileInputStream(inputFile));
                case none -> new FileInputStream(inputFile);
                case zstd -> new ZstdInputStream(new FileInputStream(inputFile));
            };
            case file -> new FileInputStream(inputFile);
        };
    }

}
