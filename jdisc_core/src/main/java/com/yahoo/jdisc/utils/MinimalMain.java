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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Minimal launcher that run the specified main method using the same Apache Felix integration as in {@link com.yahoo.jdisc.core.StandaloneMain}.
 * None of the other JDisc framework features are enabled (e.g. no DI, no server/client providers).
 * This is useful that building CLI tools that utilizes existing bundles, removing the need for building self-contained fat jars.
 *
 * This utility can later be improved by generating a local OSGi Bundle Repository from known bundle locations,
 * and then resolving dependencies when installing bundles. For now, each necessary bundle must be explicitly listed.
 *
 * <p>System properties:
 * <ul>
 *   <li>bundle.locations - Comma-separated list of bundle paths. Each path can be:
 *     <ul>
 *       <li>Absolute path: "/absolute/path/to/bundle.jar"</li>
 *       <li>Relative path: "custom/dir/bundle.jar" (resolved relative to $VESPA_HOME)</li>
 *       <li>Simple filename: "my-bundle.jar" (resolved to $VESPA_HOME/lib/jars/my-bundle.jar)</li>
 *     </ul>
 *   </li>
 *   <li>main.bundle - Bundle containing the main class (bundle symbolic name or path)</li>
 *   <li>main.class - The fully qualified class name containing main method</li>
 * </ul>
 *
 * @author bjorncs
 */
public class MinimalMain {

    static {
        // Required for jdisc-preinstall directives
        System.setProperty("jdisc.bundle.path", Defaults.getDefaults().underVespaHome("lib/jars"));
    }

    private static final String CACHE_BASE_DIR = System.getProperty("user.home") + "/.vespa/osgi-bundle-cache";

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
            System.exit(e.exitCode);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        var bundleLocations = System.getProperty("bundle.locations");
        var mainBundleName = System.getProperty("main.bundle");
        var mainClassName = System.getProperty("main.class");

        if (bundleLocations == null || bundleLocations.isEmpty()) {
            throw new LauncherException("System property 'bundle.locations' is required", 1);
        }
        if (mainBundleName == null || mainBundleName.isEmpty()) {
            throw new LauncherException("System property 'main.bundle' is required", 1);
        }
        if (mainClassName == null || mainClassName.isEmpty()) {
            throw new LauncherException("System property 'main.class' is required", 1);
        }

        var cacheDir = createFelixBundleCacheDirectory();
        var framework = new FelixFramework(
                new FelixParams()
                        .setLoggerEnabled(false) // Disable verbose Felix logging
                        .setCachePath(cacheDir.toString()));
        framework.start();

        var bundlePaths = Arrays.stream(bundleLocations.split(","))
                .map(location -> resolveBundlePath(location.trim()))
                .toList();
        var bundles = new ArrayList<Bundle>();
        for (var bundlePath : bundlePaths) {
            bundles.addAll(framework.installBundle("file:" + bundlePath));
        }
        framework.startBundles(bundles, false);

        var targetBundle = resolveMainBundle(mainBundleName, bundles, bundlePaths);
        var mainClassInstance = resolveMainClass(targetBundle, mainClassName);
        var mainMethod = resolveMainMethod(mainClassInstance, mainClassName);
        mainMethod.invoke(null, (Object) args);
    }

    private static Bundle resolveMainBundle(String mainBundle, List<Bundle> bundles, List<String> bundlePaths) {
        // First try matching by symbolic name
        for (var bundle : bundles) {
            if (mainBundle.equals(bundle.getSymbolicName())) return bundle;
        }

        // If not found, try matching by resolved path
        var resolvedMainBundle = resolveBundlePath(mainBundle);
        for (int i = 0; i < bundlePaths.size(); i++) {
            if (resolvedMainBundle.equals(bundlePaths.get(i))) return bundles.get(i);
        }
        throw new LauncherException("Main bundle not found: " + mainBundle, 1);
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

    private static Path createFelixBundleCacheDirectory() throws IOException {
        var cacheBaseDir = Path.of(CACHE_BASE_DIR);
        Files.createDirectories(cacheBaseDir);
        var cacheDir = Files.createTempDirectory(cacheBaseDir, "minimal-main-");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Files.walk(cacheDir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (IOException e) {
                System.err.println("Failed to delete cache directory: " + e.getMessage());
            }
        }));
        return cacheDir;
    }

    private static String resolveBundlePath(String bundleLocation) {
        if (!bundleLocation.endsWith(".jar")) {
            bundleLocation = bundleLocation + "-jar-with-dependencies.jar";
        }
        if (!bundleLocation.contains("/")) {
            bundleLocation = Defaults.getDefaults().underVespaHome("lib/jars/" + bundleLocation);
        }
        if (!Files.exists(Path.of(bundleLocation))) {
            throw new LauncherException("Bundle not found: " + bundleLocation, 1);
        }
        return bundleLocation;
    }
}
