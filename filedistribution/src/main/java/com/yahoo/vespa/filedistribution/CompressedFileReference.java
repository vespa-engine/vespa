// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.filedistribution;

import com.google.common.io.ByteStreams;
import com.yahoo.log.LogLevel;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Utility class for compressing and decompressing files used in a file reference
 *
 * @author hmusum
 */
public class CompressedFileReference {

    private static final Logger log = Logger.getLogger(CompressedFileReference.class.getName());
    private static final int recurseDepth = 100;

    public static File compress(File baseDir, List<File> inputFiles, File outputFile) throws IOException {
        TarArchiveOutputStream archiveOutputStream = new TarArchiveOutputStream(new GZIPOutputStream(new FileOutputStream(outputFile)));
        archiveOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
        createArchiveFile(archiveOutputStream, baseDir, inputFiles);
        return outputFile;
    }

    public static File compress(File directory, File outputFile) throws IOException {
        return compress(directory, Files.find(Paths.get(directory.getAbsolutePath()),
                recurseDepth,
                (p, basicFileAttributes) -> basicFileAttributes.isRegularFile())
                .map(Path::toFile).collect(Collectors.toList()), outputFile);
    }

    public static byte[] compress(File directory) throws IOException {
        return compress(directory, Files.find(Paths.get(directory.getAbsolutePath()),
                                              recurseDepth,
                                              (p, basicFileAttributes) -> basicFileAttributes.isRegularFile())
                .map(Path::toFile).collect(Collectors.toList()));
    }

    public static byte[] compress(File baseDir, List<File> inputFiles) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TarArchiveOutputStream archiveOutputStream = new TarArchiveOutputStream(new GZIPOutputStream(out));
        archiveOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
        createArchiveFile(archiveOutputStream, baseDir, inputFiles);
        return out.toByteArray();
    }

    static void decompress(File inputFile, File outputDir) throws IOException {
        log.log(LogLevel.DEBUG, () -> "Decompressing '" + inputFile + "' into '" + outputDir + "'");
        try (ArchiveInputStream ais = new TarArchiveInputStream(new GZIPInputStream(new FileInputStream(inputFile)))) {
            decompress(ais, outputDir);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Unable to decompress '" + inputFile.getAbsolutePath() + "': " + e.getMessage());
        }
    }

    private static void decompress(ArchiveInputStream archiveInputStream, File outputFile) throws IOException {
        int entries = 0;
        ArchiveEntry entry;
        while ((entry = archiveInputStream.getNextEntry()) != null) {
            log.log(LogLevel.DEBUG, "Unpacking " + entry.getName());
            File outFile = new File(outputFile, entry.getName());
            if (entry.isDirectory()) {
                if (!(outFile.exists() && outFile.isDirectory())) {
                    log.log(LogLevel.DEBUG, () -> "Creating dir: " + outFile.getAbsolutePath());
                    if (!outFile.mkdirs()) {
                        log.log(LogLevel.WARNING, "Could not create dir " + entry.getName());
                    }
                }
            } else {
                // Create parent dir if necessary
                File parent = new File(outFile.getParent());
                if (!parent.exists() && !parent.mkdirs()) {
                    log.log(LogLevel.WARNING, "Could not create dir " + parent.getAbsolutePath());
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
        log.log(LogLevel.DEBUG, () -> "Adding file to tar: " + baseDir.toPath().relativize(file.toPath()).toString());
        taos.putArchiveEntry(taos.createArchiveEntry(file, baseDir.toPath().relativize(file.toPath()).toString()));
        ByteStreams.copy(new FileInputStream(file), taos);
        taos.closeArchiveEntry();
    }
}

