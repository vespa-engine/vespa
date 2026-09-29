// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.concurrent.Lock;
import com.yahoo.concurrent.Locks;
import com.yahoo.config.FileReference;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.io.IOUtils;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.defaults.Defaults;
import net.jpountz.xxhash.XXHash64;
import net.jpountz.xxhash.XXHashFactory;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Logger;

import static com.yahoo.yolean.Exceptions.uncheck;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINEST;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

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
            log.log(WARNING, "Failed creating directory in constructor, will retry on demand : " + e.getMessage());
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
        if (dir.toPath().normalize().equals(root.toPath().normalize())) {
            log.log(WARNING, "File reference '" + reference.value() + "' resolves to the file reference root itself, refusing to serve it");
            return Optional.empty();
        }
        if (!dir.exists()) {
            // This is common when config server has not yet received the file from the server the app was deployed on
            log.log(FINEST, "File reference '" + reference.value() + "' ('" + dir.getAbsolutePath() + "') does not exist.");
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

    public enum HashScheme {
        LEGACY, SHA256;

        static HashScheme fromReference(FileReference reference) {
            String value = reference.value();
            if (value.matches("[0-9a-f]{1,16}")) return LEGACY;
            if (value.matches("[0-9a-f]{64}")) return SHA256;
            throw new IllegalArgumentException("Unrecognized file reference: " + value);
        }
    }

    private String computeHash(File file, HashScheme scheme) throws IOException {
        return switch (scheme) {
            case LEGACY -> Long.toHexString(computeLegacyHash(file));
            case SHA256 -> computeSha256(file);
        };
    }

    private Long computeLegacyHash(File file) throws IOException {
        XXHash64 hasher = XXHashFactory.fastestInstance().hash64();
        if (file.isDirectory()) {
            return Files.walk(file.toPath(), 100).map(path -> {
                try {
                    log.log(FINEST, () -> "Calculating hash for '" + path + "'");
                    return hash(path.toFile(), hasher);
                } catch (IOException e) {
                    log.log(WARNING, "Failed getting hash from '" + path + "'");
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

    private String computeSha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var output = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
                hash(file, output, 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    // Hash names and contents together so that renames and changes to directory structure change the reference.
    // Types, lengths and child counts make the encoding unambiguous; sorting makes it independent of traversal order.
    private void hash(File file, DataOutputStream output, int depth) throws IOException {
        if (depth > 100)
            throw new IOException("File reference exceeds the maximum directory depth: " + file);

        boolean directory = file.isDirectory();
        output.writeByte(directory ? 'd' : 'f');
        byte[] name = Utf8.toBytes(file.getName());
        output.writeInt(name.length);
        output.write(name);
        if (directory) {
            File[] children = file.listFiles();
            if (children == null)
                throw new IOException("Could not list directory: " + file);
            Arrays.sort(children, Comparator.comparing(File::getName));
            output.writeInt(children.length);
            for (File child : children)
                hash(child, output, depth + 1);
        } else {
            byte[] content;
            try {
                content = IOUtils.readFileBytes(file);
            } catch (IOException e) {
                if (depth == 0) throw e;
                // Callers may ignore FileNotFoundException for a missing resource.
                // A failed read inside a directory must still fail the entire resource.
                throw new IOException("Could not read file in directory resource: " + file, e);
            }
            output.writeLong(content.length);
            output.write(content);
        }
    }

    public FileReference addFile(File source, HashScheme scheme) throws IOException {
        return addFile(source, Optional.empty(), scheme);
    }

    public FileReference addFile(File source, Optional<ApplicationId> owner, HashScheme scheme) throws IOException {
        FileReference fileReference = new FileReference(computeHash(source, scheme));
        try (Lock lock = locks.lock(fileReference)) {
            return addFile(source, fileReference, owner);
        }
    }

    public boolean delete(FileReference fileReference, Function<FileReference, Boolean> isInUse, Function<File, Boolean> isOld) {
        try (Lock lock = locks.lock(fileReference)) {
            if (isInUse.apply(fileReference))
                log.log(FINE, "Unable to delete file reference '" + fileReference.value() + "' since it is still in use");
            else if ( ! isOld.apply(new File(getRoot(), fileReference.value())))
                log.log(FINE, "Unable to delete file reference '" + fileReference.value() + "' since it is recently used");
            else {
                deleteDirRecursively(destinationDir(fileReference));
                log.log(FINE, "Deleted file reference '" + fileReference.value() + "'");
                return true;
            }
            return false;
        }
    }

    private void deleteDirRecursively(File dir) {
        log.log(FINEST, "Will delete dir " + dir);
        if ( ! IOUtils.recursiveDeleteDir(dir))
            log.log(INFO, "Failed to delete " + dir);
    }

    // Check if we should add file, it might already exist
    private boolean shouldAddFile(File source, FileReference fileReference, Optional<ApplicationId> owner) throws IOException {
        File destinationDir = destinationDir(fileReference);
        if ( ! destinationDir.exists()) return true;

        File existingFile = destinationDir.toPath().resolve(source.getName()).toFile();
        if ( ! existingFile.exists() || ! computeHash(existingFile, HashScheme.fromReference(fileReference)).equals(fileReference.value())) {
            log.log(WARNING, "Directory for file reference '" + fileReference.value() + "'" + ownerSuffix(owner) +
                    " has content that does not match its hash, deleting everything in " +
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

    // Writes source to the destination directory unless it is already there.
    private FileReference addFile(File source, FileReference reference, Optional<ApplicationId> owner) throws IOException {
        if ( ! shouldAddFile(source, reference, owner)) return reference;

        ensureRootExist();
        Path tempDestinationDir = uncheck(() -> Files.createTempDirectory(root.toPath(), "writing"));
        try {
            logfileInfo(source, owner);

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

    private void logfileInfo(File file, Optional<ApplicationId> owner) throws IOException {
        BasicFileAttributes basicFileAttributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
        log.log(FINE, () -> "Adding file " + file.getAbsolutePath() + ownerSuffix(owner) + " (created " + basicFileAttributes.creationTime() +
                ", modified " + basicFileAttributes.lastModifiedTime() +
                ", size " + basicFileAttributes.size() + ")");
    }

    private static String ownerSuffix(Optional<ApplicationId> owner) {
        return owner.map(id -> " for application '" + id.toFullString() + "'").orElse("");
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
