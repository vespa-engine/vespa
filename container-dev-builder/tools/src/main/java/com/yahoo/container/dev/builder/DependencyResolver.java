// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.dev.builder;

import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
public class DependencyResolver {

    private static final Path DEPENDENCIES = Paths.get("dependencies");

    public static void main(String[] args) throws IOException, XmlPullParserException {
        final Set<String> blacklist = new HashSet<>(Arrays.asList(args).subList(1, args.length));
        final Set<String> dependencies = new TreeSet<>();
        Files.walkFileTree(Paths.get(args[0]), new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                if (!attrs.isRegularFile()) {
                    return FileVisitResult.CONTINUE;
                }
                if (!file.getFileName().equals(DEPENDENCIES)) {
                    return FileVisitResult.CONTINUE;
                }
                for (final String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    for (final String dependency : line.split(" ")) {
                        if (dependency == null || dependency.isEmpty()) {
                            continue;
                        }
                        final String[] arr = dependency.split(":");
                        if (arr.length != 5 || blacklist.contains(arr[0] + ":" + arr[1])) {
                            continue;
                        }
                        dependencies.add(dependency);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        for (final String dependency : dependencies) {
            System.out.println(dependency);
        }
    }
}
