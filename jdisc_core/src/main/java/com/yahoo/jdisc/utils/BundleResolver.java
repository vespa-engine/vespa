// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.utils;

import com.yahoo.text.Text;
import org.apache.felix.bundlerepository.RepositoryAdmin;
import org.apache.felix.bundlerepository.Resource;
import org.apache.felix.bundlerepository.impl.RepositoryAdminImpl;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Resolves bundle dependencies from an index file created by {@link BundleIndexer}.
 * Uses Apache Felix OBR (OSGi Bundle Repository) for dependency resolution.
 *
 * @author bjorncs
 */
class BundleResolver {
    private static final Logger log = Logger.getLogger(BundleResolver.class.getName());

    private final RepositoryAdmin repoAdmin;

    BundleResolver(BundleContext bundleContext, Path indexPath) {
        try {
            repoAdmin = new RepositoryAdminImpl(bundleContext, new org.apache.felix.utils.log.Logger(bundleContext));
            repoAdmin.addRepository(indexPath.toUri().toURL());
        } catch (Exception e) {
            throw new RuntimeException("Failed to load bundle repository from " + indexPath, e);
        }
    }

    /**
     * Resolves all bundles required to run the specified main bundle.
     * Returns bundle locations of all resolved bundles.
     */
    List<String> resolve(String mainBundleSymbolicName) {
        log.log(Level.FINE, () -> "Resolving dependencies for bundle: " + mainBundleSymbolicName);

        try {
            var resources = repoAdmin.discoverResources(Text.format("(symbolicname=%s)", mainBundleSymbolicName));
            if (resources == null || resources.length == 0) {
                throw new IllegalArgumentException("Bundle not found in repository: " + mainBundleSymbolicName);
            }
            var mainBundle = resources[0];
            log.log(Level.FINE, () -> Text.format("Found main bundle: %s v%s", mainBundle.getSymbolicName(), mainBundle.getVersion()));
            var resolver = repoAdmin.resolver();
            resolver.add(mainBundle);
            if (!resolver.resolve()) {
                throw new RuntimeException(
                        Text.format("Failed to resolve bundle dependencies: %s",
                                Arrays.stream(resolver.getUnsatisfiedRequirements())
                                        .map(m -> Text.format("%s %s", m.getRequirement(), m.getResource()))
                                        .collect(Collectors.joining())));
            }
            var resolvedBundlePaths = Stream.concat(Arrays.stream(resolver.getRequiredResources()), Arrays.stream(resolver.getAddedResources()))
                    .map(BundleResolver::extractBundlePath)
                    .distinct()
                    .toList();
            log.log(Level.FINE, () -> "Resolved bundle paths: " + resolvedBundlePaths);
            return resolvedBundlePaths;
        } catch (InvalidSyntaxException e) {
            throw new RuntimeException("Invalid filter syntax: " + e.getMessage(), e);
        }
    }

    private static String extractBundlePath(Resource resource) {
        var properties = resource.getProperties();
        if (properties != null && properties.containsKey("uri")) {
            var uri = properties.get("uri");
            if (uri instanceof String uriString) return uriString;
        }
        throw new IllegalStateException(
                Text.format("No uri property found for bundle: %s v%s", resource.getSymbolicName(), resource.getVersion()));
    }
}
