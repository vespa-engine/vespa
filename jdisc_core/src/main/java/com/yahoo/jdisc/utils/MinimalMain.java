// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.utils;

import com.yahoo.jdisc.core.FelixFramework;
import com.yahoo.jdisc.core.FelixParams;
import com.yahoo.vespa.defaults.Defaults;
import org.osgi.framework.Bundle;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Minimal launcher that runs the specified main method using the same Apache Felix integration as in {@link com.yahoo.jdisc.core.StandaloneMain}.
 * None of the other JDisc framework features are enabled (e.g. no DI, no server/client providers).
 * This is useful for building CLI tools that utilize existing bundles, removing the need for building self-contained fat jars.
 *
 * <p>This utility uses automatic bundle dependency resolution. It scans available bundles in {@code $VESPA_HOME/lib/jars}
 * and additional directories specified in {@code bundle.additionalLocations}, creates an index (stored per main bundle symbolic name),
 * and automatically resolves and installs all required dependencies for the main bundle.
 *
 * <p>System properties:
 * <ul>
 *   <li>bundle.additionalLocations - (Optional) Comma-separated list of additional directories to scan for bundles.
 *     Each directory path can be:
 *     <ul>
 *       <li>Absolute path: "/absolute/path/to/bundle/dir"</li>
 *       <li>Relative path: "custom/dir" (resolved relative to $VESPA_HOME)</li>
 *       <li>Simple name: "mydir" (resolved to $VESPA_HOME/lib/jars/mydir)</li>
 *     </ul>
 *   </li>
 *   <li>bundle.denylist - (Optional) Regular expression pattern to exclude bundles from scanning. Example: {@code "(foo|bar)\.jar"}</li>
 *   <li>main.bundle - Bundle symbolic name of the bundle containing the main class</li>
 *   <li>main.class - The fully qualified class name containing main method</li>
 * </ul>
 *
 * @author bjorncs
 */
public class MinimalMain {
    private static final String CACHE_BASE_DIR = Defaults.getDefaults().underVespaHome("var/vespa/bundlecache/minimalmain");

    private static final Logger log = Logger.getLogger(MinimalMain.class.getName());

    static {
        System.setProperty("jdisc.bundle.path", Defaults.getDefaults().underVespaHome("lib/jars/"));
    }

    private static class LauncherException extends RuntimeException {
        final int exitCode;

        LauncherException(String message, int exitCode) {
            super(message);
            this.exitCode = exitCode;
        }

        LauncherException(String message, int exitCode, Throwable cause) {
            super(message, cause);
            this.exitCode = exitCode;
        }
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (LauncherException e) {
            System.err.println(e.getMessage());
            e.printStackTrace(System.err);
            System.exit(e.exitCode);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        var additionalLocations = System.getProperty("bundle.additionalLocations");
        var mainBundleName = System.getProperty("main.bundle");
        var mainClassName = System.getProperty("main.class");

        if (mainBundleName == null || mainBundleName.isEmpty()) {
            throw new LauncherException("System property 'main.bundle' is required", 1);
        }
        if (mainClassName == null || mainClassName.isEmpty()) {
            throw new LauncherException("System property 'main.class' is required", 1);
        }

        var cacheDir = createFelixBundleCacheDirectory(mainClassName);
        var framework = new FelixFramework(
                new FelixParams()
                        .setLoggerEnabled(false) // Disable verbose Felix logging
                        .setCachePath(cacheDir.toString()));
        framework.start();

        var additionalDirectories = parseBundleLocationDirectories(additionalLocations);
        var bundlePaths = resolveRequiredBundles(framework, mainBundleName, additionalDirectories);

        var bundles = new LinkedHashSet<Bundle>();
        for (var bundlePath : bundlePaths) {
            bundles.addAll(framework.installBundle(bundlePath));

        }
        framework.startBundles(List.copyOf(bundles), false);

        var targetBundle = resolveMainBundle(mainBundleName, bundles);
        var mainClassInstance = resolveMainClass(targetBundle, mainClassName);
        var mainMethod = resolveMainMethod(mainClassInstance, mainClassName);
        mainMethod.invoke(null, (Object) args);
    }

    private static List<String> resolveRequiredBundles(
            FelixFramework framework, String mainBundleSymbolicName, List<String> additionalDirectories) {
        try {
            var denylistPattern = createDenylistPattern();
            var bundleIndexer = new BundleIndexer(Path.of(Defaults.getDefaults().underVespaHome("lib/jars")), denylistPattern);
            var indexPath = bundleIndexer.createIndexIfMissing(additionalDirectories, mainBundleSymbolicName);
            return new BundleResolver(framework.bundleContext(), indexPath).resolve(mainBundleSymbolicName);
        } catch (Exception e) {
            throw new LauncherException("Failed to resolve bundle dependencies: " + e.getMessage(), 1, e);
        }
    }

    private static Pattern createDenylistPattern() {
        var denylistProperty = System.getProperty("bundle.denylist");
        if (denylistProperty == null || denylistProperty.isEmpty()) return null;
        return Pattern.compile(denylistProperty);
    }

    private static List<String> parseBundleLocationDirectories(String bundleLocations) {
        if (bundleLocations == null || bundleLocations.isEmpty()) return List.of();
        return Arrays.stream(bundleLocations.split(","))
                .map(String::trim)
                .map(MinimalMain::resolveBundleDirectory)
                .toList();
    }

    private static String resolveBundleDirectory(String location) {
        if (location.startsWith("/")) return location;
        if (location.contains("/")) return Defaults.getDefaults().underVespaHome(location);
        return Defaults.getDefaults().underVespaHome("lib/jars/" + location);
    }

    private static Bundle resolveMainBundle(String mainBundleSymbolicName, Collection<Bundle> bundles) {
        for (var bundle : bundles) {
            if (mainBundleSymbolicName.equals(bundle.getSymbolicName())) return bundle;
        }
        throw new LauncherException("Main bundle not found: " + mainBundleSymbolicName, 1);
    }

    private static Method resolveMainMethod(Class<?> mainClassInstance, String mainClass) {
        try {
            return mainClassInstance.getMethod("main", String[].class);
        } catch (NoSuchMethodException e) {
            throw new LauncherException("No main(String[] args) method found in class: " + mainClass, 1, e);
        }
    }

    private static Class<?> resolveMainClass(Bundle targetBundle, String mainClass) {
        try {
            return targetBundle.loadClass(mainClass);
        } catch (ClassNotFoundException e) {
            throw new LauncherException("Class not found in bundle '" + targetBundle.getSymbolicName() + "': " + mainClass, 1, e);
        }
    }

    private static Path createFelixBundleCacheDirectory(String mainClassName) throws IOException {
        var cacheBaseDir = Path.of(CACHE_BASE_DIR);
        Files.createDirectories(cacheBaseDir);
        var cacheDir = Files.createTempDirectory(cacheBaseDir, mainClassName + "-");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try (var paths = Files.walk(cacheDir)) {
                paths.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (IOException e) {
                log.warning("Failed to delete cache directory: " + e.getMessage());
            }
        }));
        return cacheDir;
    }
}
