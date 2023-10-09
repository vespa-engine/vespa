// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.concurrent.Lock;
import com.yahoo.concurrent.Locks;
import com.yahoo.config.FileReference;
import com.yahoo.io.IOUtils;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.defaults.Defaults;
import net.jpountz.xxhash.XXHash64;
import net.jpountz.xxhash.XXHashFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.yolean.Exceptions.uncheck;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;

/**
 * Global file directory, holding files for file distribution for all deployed applications.
 *
 */
public class FileDirectory extends AbstractComponent {

    private static final Logger log = Logger.getLogger(FileDirectory.class.getName());

    private final Locks<FileReference> locks = new Locks<>(1, TimeUnit.MINUTES);
    private final File root;

    @Inject
    public FileDirectory(ConfigserverConfig configserverConfig) {
        this(new File(Defaults.getDefaults().underVespaHome(configserverConfig.fileReferencesDir())));
    }

    public FileDirectory(File rootDir) {
        this.root = rootDir;
        try {
            ensureRootExist();
        } catch (IllegalArgumentException e) {
            log.log(Level.WARNING, "Failed creating directory in constructor, will retry on demand : " + e.getMessage());
        }
    }

    private void ensureRootExist() {
        if (! root.exists()) {
            if ( ! root.mkdir()) {
                throw new IllegalArgumentException("Failed creating root dir '" + root.getAbsolutePath() + "'.");
            }
        } else if (!root.isDirectory()) {
            throw new IllegalArgumentException("'" + root.getAbsolutePath() + "' is not a directory");
        }
    }

    private static class Filter implements FilenameFilter {
        @Override
        public boolean accept(File dir, String name) {
            return !".".equals(name) && !"..".equals(name) ;
        }
    }

    String getPath(FileReference ref) {
        return root.getAbsolutePath() + "/" + ref.value();
    }

    public Optional<File> getFile(FileReference reference) {
        ensureRootExist();
        File dir = new File(getPath(reference));
        if (!dir.exists()) {
            // This is common when config server has not yet received the file from one the server the app was deployed on
            log.log(FINE, "File reference '" + reference.value() + "' ('" + dir.getAbsolutePath() + "') does not exist.");
            return Optional.empty();
        }
        if (!dir.isDirectory()) {
            log.log(INFO, "File reference '" + reference.value() + "' ('" + dir.getAbsolutePath() + ")' is not a directory.");
            return Optional.empty();
        }
        File[] files = dir.listFiles(new Filter());
        if (files == null || files.length == 0) {
            log.log(INFO, "File reference '" + reference.value() + "' ('" + dir.getAbsolutePath() + "') does not contain any files");
            return Optional.empty();
        }
        return Optional.of(files[0]);
    }

    public File getRoot() { return root; }

    private Long computeHash(File file) throws IOException {
        XXHash64 hasher = XXHashFactory.fastestInstance().hash64();
        if (file.isDirectory()) {
            return Files.walk(file.toPath(), 100).map(path -> {
                try {
                    log.log(Level.FINEST, () -> "Calculating hash for '" + path + "'");
                    return hash(path.toFile(), hasher);
                } catch (IOException e) {
                    log.log(Level.WARNING, "Failed getting hash from '" + path + "'");
                    return 0;
                }
            }).mapToLong(Number::longValue).sum();
        } else {
            return hash(file, hasher);
        }
    }

    private long hash(File file, XXHash64 hasher) throws IOException {
        byte[] wholeFile = file.isDirectory() ?  new byte[0] : IOUtils.readFileBytes(file);
        return hasher.hash(ByteBuffer.wrap(wholeFile), hasher.hash(ByteBuffer.wrap(Utf8.toBytes(file.getName())), 0));
    }

    public FileReference addFile(File source) throws IOException {
        Long hash = computeHash(source);
        FileReference fileReference = fileReferenceFromHash(hash);

        try (Lock lock = locks.lock(fileReference)) {
            return addFile(source, fileReference, hash);
        }
    }

    public void delete(FileReference fileReference, Function<FileReference, Boolean> isInUse) {
        try (Lock lock = locks.lock(fileReference)) {
            if (isInUse.apply(fileReference))
                log.log(FINE, "Unable to delete file reference '" + fileReference.value() + "' since it is still in use");
            else
                deleteDirRecursively(destinationDir(fileReference));
        }
    }

    private void deleteDirRecursively(File dir) {
        log.log(FINE, "Will delete dir " + dir);
        if ( ! IOUtils.recursiveDeleteDir(dir))
            log.log(INFO, "Failed to delete " + dir);
    }

    // Check if we should add file, it might already exist
    private boolean shouldAddFile(File source, Long hashOfFileToBeAdded) throws IOException {
        FileReference fileReference = fileReferenceFromHash(hashOfFileToBeAdded);
        File destinationDir = destinationDir(fileReference);
        if ( ! destinationDir.exists()) return true;

        File existingFile = destinationDir.toPath().resolve(source.getName()).toFile();
        if ( ! existingFile.exists() || ! computeHash(existingFile).equals(hashOfFileToBeAdded)) {
            log.log(Level.WARNING, "Directory for file reference '" + fileReference.value() +
                    "' has content that does not match its hash, deleting everything in " +
                    destinationDir.getAbsolutePath());
            deleteDirRecursively(destinationDir);
            return true;
        }

        // update last modified time so that maintainer deleting unused file references considers this as recently used
        destinationDir.setLastModified(Clock.systemUTC().instant().toEpochMilli());
        log.log(FINE, "Directory for file reference '" + fileReference.value() + "' already exists and has all content");
        return false;
    }

    private File destinationDir(FileReference fileReference) {
        return new File(root, fileReference.value());
    }

    private FileReference fileReferenceFromHash(Long hash) {
        return new FileReference(Long.toHexString(hash));
    }

    // Pre-condition: Destination dir does not exist
    private FileReference addFile(File source, FileReference reference, Long hash) throws IOException {
        if ( ! shouldAddFile(source, hash)) return reference;

        ensureRootExist();
        Path tempDestinationDir = uncheck(() -> Files.createTempDirectory(root.toPath(), "writing"));
        try {
            logfileInfo(source);

            // Copy files to temp dir
            File tempDestination = new File(tempDestinationDir.toFile(), source.getName());
            log.log(FINE, () -> "Copying " + source.getAbsolutePath() + " to " + tempDestination.getAbsolutePath());
            if (source.isDirectory())
                IOUtils.copyDirectory(source, tempDestination, -1);
            else
                copyFile(source, tempDestination);

            // Move to destination dir
            Path destinationDir = destinationDir(reference).toPath();
            log.log(FINE, () -> "Moving " + tempDestinationDir + " to " + destinationDir);
            Files.move(tempDestinationDir, destinationDir);
            return reference;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            IOUtils.recursiveDeleteDir(tempDestinationDir.toFile());
        }
    }

    private void logfileInfo(File file ) throws IOException {
        BasicFileAttributes basicFileAttributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
        log.log(FINE, () -> "Adding file " + file.getAbsolutePath() + " (created " + basicFileAttributes.creationTime() +
                ", modified " + basicFileAttributes.lastModifiedTime() +
                ", size " + basicFileAttributes.size() + ")");
    }

    private static void copyFile(File source, File dest) throws IOException {
        try (FileChannel sourceChannel = new FileInputStream(source).getChannel();
             FileChannel destChannel = new FileOutputStream(dest).getChannel()) {
            destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
        }
    }

    @Override
    public String toString() {
        return "root dir: " + root.getAbsolutePath();
    }

}
