// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.utils;

import com.yahoo.vespa.defaults.Defaults;
import org.apache.felix.bundlerepository.DataModelHelper;
import org.apache.felix.bundlerepository.Resource;
import org.apache.felix.bundlerepository.impl.DataModelHelperImpl;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Scans directories for OSGi bundles and creates a repository file for bundle resolution.
 * See <a href="https://felix.apache.org/documentation/subprojects/apache-felix-osgi-bundle-repository.html">Felix OBR</a> for details.
 *
 * @author bjorncs
 */
class BundleIndexer {

    private static final Logger log = Logger.getLogger(BundleIndexer.class.getName());

    private final DataModelHelper dataModelHelper = new DataModelHelperImpl();
    private final Path libJarsDirectory;
    private final Pattern blacklistPattern;

    BundleIndexer(Path libJarsDirectory, Pattern blacklistPattern) {
        this.libJarsDirectory = libJarsDirectory;
        this.blacklistPattern = blacklistPattern;
    }

    Path createIndexIfMissing(List<String> additionalDirectories, String mainBundleSymbolicName) throws IOException {
        return createIndexIfMissing(additionalDirectories, getIndexPath(mainBundleSymbolicName));
    }

    Path createIndexIfMissing(List<String> additionalDirectories, Path indexPath) throws IOException {
        if (Files.exists(indexPath)) return indexPath;
        return createIndex(additionalDirectories, indexPath);
    }

    private Path createIndex(List<String> additionalDirectories, Path indexPath) throws IOException {
        var resources = scanBundles(additionalDirectories);
        writeIndex(resources, indexPath);
        log.log(Level.FINE, () -> "Created index with %d bundles".formatted(resources.size()));
        return indexPath;
    }

    private List<Resource> scanBundles(List<String> additionalDirectories) throws IOException {
        var directories = new ArrayList<String>();
        directories.add(libJarsDirectory.toString());
        directories.addAll(additionalDirectories);

        var resources = new ArrayList<Resource>();
        for (var directory : directories) {
            try (Stream<Path> files = Files.list(Path.of(directory))) {
                files.filter(path -> path.toString().endsWith(".jar"))
                     .filter(this::isNotBlacklisted)
                     .forEach(jarPath -> readBundleResource(jarPath).ifPresent(resources::add));
            }
        }
        log.log(Level.FINE, () ->
                "Bundle scan complete: found %d bundles across %d directories".formatted(resources.size(), directories.size()));
        return resources;
    }

    private boolean isNotBlacklisted(Path jarPath) {
        if (blacklistPattern == null) return true;
        var fileName = jarPath.getFileName().toString();
        return !blacklistPattern.matcher(fileName).matches();
    }

    private Optional<Resource> readBundleResource(Path jarPath) {
        try {
            var resource = dataModelHelper.createResource(jarPath.toUri().toURL());
            if (resource == null) {
                log.log(Level.FINE, () -> "Skipping " + jarPath + ": no Bundle-SymbolicName");
                return Optional.empty();
            }
            log.log(Level.FINE, () ->
                    "Read bundle %s v%s from %s".formatted(resource.getSymbolicName(), resource.getVersion(), jarPath));
            return Optional.of(resource);
        } catch (Exception e) {
            log.log(Level.FINE, () -> "Failed to read bundle from %s: %s".formatted(jarPath, e.getMessage()));
            return Optional.empty();
        }
    }

    private void writeIndex(List<Resource> resources, Path indexPath) throws IOException {
        var tempFile = Files.createTempFile(indexPath.getParent(), ".bundle-index-", ".tmp");
        try {
            try (var writer = new OutputStreamWriter(Files.newOutputStream(tempFile), StandardCharsets.UTF_8)) {
                dataModelHelper.writeRepository(dataModelHelper.repository(resources.toArray(Resource[]::new)), writer);
            }
            Files.move(tempFile, indexPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            Files.deleteIfExists(tempFile);
            throw new IOException("Failed to write repository index: " + e.getMessage(), e);
        }
    }

    private static Path getIndexPath(String mainBundleSymbolicName) {
        return Path.of(Defaults.getDefaults().underVespaHome("tmp/bundle-index-" + mainBundleSymbolicName + ".xml"));
    }
}
