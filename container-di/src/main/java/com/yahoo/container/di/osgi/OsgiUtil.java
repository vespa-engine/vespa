// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di.osgi;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.osgi.maven.ProjectBundleClassPaths;
import com.yahoo.osgi.maven.ProjectBundleClassPaths.BundleClasspathMapping;
import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleWiring;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.google.common.io.Files.fileTreeTraverser;

/**
 * Tested by com.yahoo.application.container.jersey.JerseyTest
 *
 * @author Tony Vaagenes
 * @author ollivir
 */
public class OsgiUtil {
    private static final Logger log = Logger.getLogger(OsgiUtil.class.getName());
    private static final String CLASS_FILE_TYPE_SUFFIX = ".class";

    public static Collection<String> getClassEntriesInBundleClassPath(Bundle bundle, Set<String> packagesToScan) {
        BundleWiring bundleWiring = bundle.adapt(BundleWiring.class);

        if (packagesToScan.isEmpty()) {
            return bundleWiring.listResources("/", "*" + CLASS_FILE_TYPE_SUFFIX,
                    BundleWiring.LISTRESOURCES_LOCAL | BundleWiring.LISTRESOURCES_RECURSE);
        } else {
            List<String> ret = new ArrayList<>();
            for (String pkg : packagesToScan) {
                ret.addAll(bundleWiring.listResources(packageToPath(pkg), "*" + CLASS_FILE_TYPE_SUFFIX, BundleWiring.LISTRESOURCES_LOCAL));
            }
            return ret;
        }
    }

    public static Collection<String> getClassEntriesForBundleUsingProjectClassPathMappings(ClassLoader classLoader,
            ComponentSpecification bundleSpec, Set<String> packagesToScan) {
        return classEntriesFrom(bundleClassPathMapping(bundleSpec, classLoader).classPathElements, packagesToScan);
    }

    private static BundleClasspathMapping bundleClassPathMapping(ComponentSpecification bundleSpec, ClassLoader classLoader) {
        ProjectBundleClassPaths projectBundleClassPaths = loadProjectBundleClassPaths(classLoader);

        if (projectBundleClassPaths.mainBundle.bundleSymbolicName.equals(bundleSpec.getName())) {
            return projectBundleClassPaths.mainBundle;
        } else {
            log.log(Level.WARNING,
                    "Dependencies of the bundle " + bundleSpec + " will not be scanned. Please file a feature request if you need this");
            return matchingBundleClassPathMapping(bundleSpec, projectBundleClassPaths.providedDependencies);
        }
    }

    public static BundleClasspathMapping matchingBundleClassPathMapping(ComponentSpecification bundleSpec,
            Collection<BundleClasspathMapping> providedBundlesClassPathMappings) {
        for (BundleClasspathMapping mapping : providedBundlesClassPathMappings) {
            if (mapping.bundleSymbolicName.equals(bundleSpec.getName())) {
                return mapping;
            }
        }
        throw new RuntimeException("No such bundle: " + bundleSpec);
    }

    private static ProjectBundleClassPaths loadProjectBundleClassPaths(ClassLoader classLoader) {
        URL classPathMappingsFileLocation = classLoader.getResource(ProjectBundleClassPaths.CLASSPATH_MAPPINGS_FILENAME);
        if (classPathMappingsFileLocation == null) {
            throw new RuntimeException("Couldn't find " + ProjectBundleClassPaths.CLASSPATH_MAPPINGS_FILENAME + "  in the class path.");
        }

        try {
            return ProjectBundleClassPaths.load(Paths.get(classPathMappingsFileLocation.toURI()));
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static Collection<String> classEntriesFrom(List<String> classPathEntries, Set<String> packagesToScan) {
        Set<String> packagePathsToScan = packagesToScan.stream().map(OsgiUtil::packageToPath).collect(Collectors.toSet());
        List<String> ret = new ArrayList<>();

        for (String entry : classPathEntries) {
            Path path = Paths.get(entry);
            if (Files.isDirectory(path)) {
                ret.addAll(classEntriesInPath(path, packagePathsToScan));
            } else if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar")) {
                ret.addAll(classEntriesInJar(path, packagePathsToScan));
            } else {
                throw new RuntimeException("Unsupported path " + path + " in the class path");
            }
        }
        return ret;
    }

    private static String relativePathToClass(Path rootPath, Path pathToClass) {
        Path relativePath = rootPath.relativize(pathToClass);
        return relativePath.toString();
    }

    private static Collection<String> classEntriesInPath(Path rootPath, Collection<String> packagePathsToScan) {
        Iterable<File> fileIterator;
        if (packagePathsToScan.isEmpty()) {
            fileIterator = fileTreeTraverser().preOrderTraversal(rootPath.toFile());
        } else {
            List<File> files = new ArrayList<>();
            for (String packagePath : packagePathsToScan) {
                for (File file : fileTreeTraverser().children(rootPath.resolve(packagePath).toFile())) {
                    files.add(file);
                }
            }
            fileIterator = files;
        }

        List<String> ret = new ArrayList<>();
        for (File file : fileIterator) {
            if (file.isFile() && file.getName().endsWith(CLASS_FILE_TYPE_SUFFIX)) {
                ret.add(relativePathToClass(rootPath, file.toPath()));
            }
        }
        return ret;
    }

    private static String packagePath(String name) {
        int index = name.lastIndexOf('/');
        if (index < 0) {
            return name;
        } else {
            return name.substring(0, index);
        }
    }

    private static Collection<String> classEntriesInJar(Path jarPath, Set<String> packagePathsToScan) {
        Predicate<String> acceptedPackage;
        if (packagePathsToScan.isEmpty()) {
            acceptedPackage = ign -> true;
        } else {
            acceptedPackage = name -> packagePathsToScan.contains(packagePath(name));
        }

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            return jarFile.stream().map(JarEntry::getName).filter(name -> name.endsWith(CLASS_FILE_TYPE_SUFFIX)).filter(acceptedPackage)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String packageToPath(String packageName) {
        return packageName.replace('.', '/');
    }
}
