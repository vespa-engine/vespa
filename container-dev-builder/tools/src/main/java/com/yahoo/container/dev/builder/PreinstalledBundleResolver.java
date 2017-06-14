// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.dev.builder;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.jar.Manifest;

public class PreinstalledBundleResolver {

    private static final Path MANIFEST_MF = Paths.get("MANIFEST.MF");
    private static final String X_JDISC_PREINSTALL_BUNDLE = "X-JDisc-Preinstall-Bundle";
    private static final String REMOVABLE_SUFFIX = ".jar";
    private static final String REMOVABLE_ASSEMBLY_ID = "-jar-with-dependencies";

    public static void main(final String[] args) throws Throwable {
        Files.walkFileTree(Paths.get(args[0]), new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                if (!attrs.isRegularFile()) {
                    return FileVisitResult.CONTINUE;
                }
                if (!file.getFileName().equals(MANIFEST_MF)) {
                    return FileVisitResult.CONTINUE;
                }
                final String preinstall = new Manifest(Files.newInputStream(file))
                        .getMainAttributes()
                        .getValue(X_JDISC_PREINSTALL_BUNDLE);
                if (preinstall == null) {
                    return FileVisitResult.CONTINUE;
                }
                for (String bundle : preinstall.split(",")) {
                    printDependency(args[1], bundle);
                }
                return super.visitFile(file, attrs);
            }
        });
    }

    private static void printDependency(String pomFormat, String bundle) throws IOException {
        bundle = bundle.trim();
        if (bundle.isEmpty()) {
            return;
        }
        if (bundle.endsWith(REMOVABLE_SUFFIX)) {
            bundle = bundle.substring(0, bundle.length() - REMOVABLE_SUFFIX.length());
        }
        if (bundle.endsWith(REMOVABLE_ASSEMBLY_ID)) {
            bundle = bundle.substring(0, bundle.length() - REMOVABLE_ASSEMBLY_ID.length());
        }
        Path pom = Paths.get(String.format(pomFormat, bundle));
        if (!Files.exists(pom)) {
            return;
        }
        Model model;
        try {
            model = new MavenXpp3Reader().read(Files.newBufferedReader(pom, StandardCharsets.UTF_8));
        } catch (XmlPullParserException e) {
            e.printStackTrace();
            return;
        }
        model.setPomFile(pom.toFile());
        final MavenProject project = new MavenProject(model);
        System.out.println(project.getGroupId() + ":" +
                           project.getArtifactId() + ":jar:" +
                           project.getVersion() + ":compile");
    }
}
