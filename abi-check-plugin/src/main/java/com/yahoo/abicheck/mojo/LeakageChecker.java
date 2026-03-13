// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.abicheck.mojo;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.abicheck.classtree.ClassFileTree;
import com.yahoo.abicheck.classtree.ClassFileTree.ClassFile;
import com.yahoo.abicheck.collector.AnnotationCollector;
import com.yahoo.abicheck.collector.LeakageSignatureCollector;
import com.yahoo.abicheck.setmatcher.SetMatcher;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.objectweb.asm.ClassReader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@Mojo(
    name = "leakagecheck",
    defaultPhase = LifecyclePhase.PACKAGE,
    requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
    instantiationStrategy = InstantiationStrategy.PER_LOOKUP,
    threadSafe = true
)
public class LeakageChecker extends AbstractMojo {

    private static final String DEFAULT_SPEC_FILE = "leakage-spec.json";
    private static final String WRITE_SPEC_PROPERTY = "leakage.writeSpec";

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(required = true)
    private String publicApiAnnotation;

    @Parameter
    private String leakageSpecFileName = DEFAULT_SPEC_FILE;

    // --- Spec file I/O ---

    private static Map<String, Set<String>> readSpec(File file) throws IOException {
        try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            ObjectMapper mapper = new ObjectMapper();
            JavaType type = mapper.getTypeFactory().constructMapType(
                    TreeMap.class,
                    mapper.getTypeFactory().constructType(String.class),
                    mapper.getTypeFactory().constructCollectionType(TreeSet.class, String.class));
            return mapper.readValue(reader, type);
        }
    }

    private static void writeSpec(Map<String, Set<String>> leakages, File file) throws IOException {
        TreeMap<String, List<String>> sorted = new TreeMap<>();
        for (var entry : leakages.entrySet()) {
            sorted.put(entry.getKey(), new ArrayList<>(new TreeSet<>(entry.getValue())));
        }
        try (FileWriter writer = new FinalNewlineWriter(file)) {
            new ObjectMapper()
                    .writer(new DefaultPrettyPrinter()
                            .withArrayIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE))
                    .writeValue(writer, sorted);
        }
    }

    private static class FinalNewlineWriter extends FileWriter {
        private boolean closed = false;
        FinalNewlineWriter(File file) throws IOException { super(file, StandardCharsets.UTF_8); }
        @Override
        public void close() throws IOException {
            if (closed) return;
            write("\n");
            super.close();
            closed = true;
        }
    }

    // --- Dependency scanning ---

    static Set<String> collectPublicApiPackagesFromJar(JarFile jarFile, String publicApiAnnotation)
            throws IOException {
        Set<String> packages = new HashSet<>();
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (!entry.isDirectory() && entry.getName().endsWith("/package-info.class")) {
                try (InputStream is = jarFile.getInputStream(entry)) {
                    AnnotationCollector visitor = new AnnotationCollector();
                    new ClassReader(is).accept(visitor,
                            ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    if (visitor.getAnnotations().contains(publicApiAnnotation)) {
                        String path = entry.getName();
                        String pkg = path.substring(0, path.lastIndexOf('/')).replace('/', '.');
                        packages.add(pkg);
                    }
                }
            }
        }
        return packages;
    }

    // --- Type collection from module JAR ---

    static Map<String, Set<String>> collectReferencedTypes(
            ClassFileTree.Package pkg, Set<String> publicApiPackages) throws IOException {
        Map<String, Set<String>> types = new LinkedHashMap<>();
        if (publicApiPackages.contains(pkg.getFullyQualifiedName())) {
            LeakageSignatureCollector collector = new LeakageSignatureCollector();
            List<ClassFile> sortedClassFiles = pkg.getClassFiles().stream()
                    .sorted(Comparator.comparing(ClassFile::getName)).toList();
            for (ClassFile klazz : sortedClassFiles) {
                try (InputStream is = klazz.getInputStream()) {
                    new ClassReader(is).accept(collector, 0);
                }
            }
            types.putAll(collector.getReferencedTypes());
        }
        for (ClassFileTree.Package subPkg : pkg.getSubPackages().stream()
                .sorted(Comparator.comparing(ClassFileTree.Package::getFullyQualifiedName)).toList()) {
            types.putAll(collectReferencedTypes(subPkg, publicApiPackages));
        }
        return types;
    }

    // --- Filtering ---

    static Map<String, Set<String>> filterLeakages(
            Map<String, Set<String>> referencedTypes, Set<String> publicApiPackages) {
        Map<String, Set<String>> leakages = new TreeMap<>();
        for (var entry : referencedTypes.entrySet()) {
            Set<String> leaked = new TreeSet<>();
            for (String type : entry.getValue()) {
                String pkg = packageOf(type);
                if (!isStandardLibrary(pkg) && !publicApiPackages.contains(pkg)) {
                    leaked.add(type);
                }
            }
            if (!leaked.isEmpty()) {
                leakages.put(entry.getKey(), leaked);
            }
        }
        return leakages;
    }

    static String packageOf(String className) {
        int lastDot = className.lastIndexOf('.');
        return lastDot >= 0 ? className.substring(0, lastDot) : "";
    }

    static boolean isVespaPackage(String pkg) {
        return pkg.startsWith("com.yahoo.") || pkg.startsWith("ai.vespa.");
    }

    static boolean isStandardLibrary(String pkg) {
        return pkg.startsWith("java.")
            || pkg.startsWith("javax.")
            || pkg.startsWith("org.w3c.dom")
            || pkg.startsWith("org.xml.sax")
            || pkg.startsWith("org.ietf.jgss");
    }

    // --- Comparison ---

    record CompareResult(boolean hasNewLeakages, boolean hasStaleEntries) {
        boolean matches() { return !hasNewLeakages && !hasStaleEntries; }
    }

    static CompareResult compareLeakages(Map<String, Set<String>> expected,
                                         Map<String, Set<String>> actual, Log log) {
        boolean[] hasNew = {false};
        boolean[] hasStale = {false};
        SetMatcher.compare(expected.keySet(), actual.keySet(),
                className -> SetMatcher.compare(expected.get(className), actual.get(className),
                        item -> true,
                        item -> { hasStale[0] = true; log.error(String.format(Locale.ROOT,
                                "Class %s: Stale leaked type (remove from spec): %s", className, item)); },
                        item -> { hasNew[0] = true; log.error(String.format(Locale.ROOT,
                                "Class %s: New leaked type: %s", className, item)); }),
                className -> { hasStale[0] = true; log.error(String.format(Locale.ROOT,
                        "Stale class in leakage spec (no longer has leakages): %s", className)); },
                className -> { hasNew[0] = true; log.error(String.format(Locale.ROOT,
                        "New leakage in class %s: %s", className, actual.get(className))); });
        return new CompareResult(hasNew[0], hasStale[0]);
    }

    // --- Mojo execution ---

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Artifact mainArtifact = project.getArtifact();
        File specFile = new File(project.getBasedir(), leakageSpecFileName);

        if (mainArtifact.getFile() == null) {
            throw new MojoExecutionException("Missing project artifact file");
        } else if (!mainArtifact.getFile().getName().endsWith(".jar")) {
            throw new MojoExecutionException(
                    "Project artifact is not a JAR: " + mainArtifact.getFile().getName());
        }

        try {
            // Build @PublicApi package set from module and all dependencies
            Set<String> publicApiPackages = new HashSet<>();
            try (JarFile moduleJar = new JarFile(mainArtifact.getFile())) {
                publicApiPackages.addAll(
                        collectPublicApiPackagesFromJar(moduleJar, publicApiAnnotation));
            }
            for (Artifact dep : project.getArtifacts()) {
                if (dep.getFile() != null && dep.getFile().getName().endsWith(".jar")) {
                    try (JarFile depJar = new JarFile(dep.getFile())) {
                        publicApiPackages.addAll(
                                collectPublicApiPackagesFromJar(depJar, publicApiAnnotation));
                    }
                }
            }
            getLog().debug("Found " + publicApiPackages.size() + " @PublicApi packages");

            // Collect referenced types from public classes in @PublicApi packages
            Map<String, Set<String>> referencedTypes;
            try (JarFile moduleJar = new JarFile(mainArtifact.getFile())) {
                ClassFileTree tree = ClassFileTree.fromJar(moduleJar);
                referencedTypes = new LinkedHashMap<>();
                for (ClassFileTree.Package pkg : tree.getRootPackages()) {
                    referencedTypes.putAll(collectReferencedTypes(pkg, publicApiPackages));
                }
            }

            // Filter to only leaked (non-public-API, non-standard-library) types
            Map<String, Set<String>> leakages = filterLeakages(referencedTypes, publicApiPackages);

            if (System.getProperty(WRITE_SPEC_PROPERTY) != null) {
                if (!leakages.isEmpty()) {
                    getLog().info("Writing leakage spec to " + specFile.getPath()
                            + " (" + leakages.size() + " classes with leakages)");
                    writeSpec(leakages, specFile);
                } else if (specFile.exists()) {
                    if (specFile.delete()) {
                        getLog().info("No leakages found, removed " + specFile.getPath());
                    } else {
                        getLog().warn("No leakages found, but failed to remove " + specFile.getPath());
                    }
                } else {
                    getLog().info("No leakages found");
                }
            } else {
                if (!specFile.exists() && leakages.isEmpty()) {
                    return;
                }
                if (!specFile.exists()) {
                    for (var entry : leakages.entrySet()) {
                        getLog().error(String.format(Locale.ROOT,
                                "New leakage in class %s: %s", entry.getKey(), entry.getValue()));
                    }
                    throw new MojoFailureException(
                            "Public API leakage detected. @PublicApi classes must not expose types from non-@PublicApi packages.\n"
                            + "Fix the leakage by using @PublicApi types in public signatures.\n"
                            + "If the risk has been assessed, grandfather with: mvn package -Dleakage.writeSpec");
                }
                Map<String, Set<String>> specLeakages = readSpec(specFile);
                CompareResult result = compareLeakages(specLeakages, leakages, getLog());
                if (!result.matches()) {
                    StringBuilder msg = new StringBuilder("Leakage spec mismatch.");
                    if (result.hasNewLeakages()) {
                        msg.append("\n@PublicApi classes must not expose types from non-@PublicApi packages.")
                           .append("\nFix the leakage by using @PublicApi types in public signatures.");
                    }
                    if (result.hasStaleEntries()) {
                        msg.append("\nStale entries indicate fixed leakages. Update the spec to remove them.");
                    }
                    msg.append("\nTo update the spec run: mvn package -Dleakage.writeSpec");
                    if (result.hasNewLeakages()) {
                        msg.append("\nOnly do this if the risk of the new leakage has been assessed.");
                    }
                    throw new MojoFailureException(msg.toString());
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Error during leakage check", e);
        }
    }
}
