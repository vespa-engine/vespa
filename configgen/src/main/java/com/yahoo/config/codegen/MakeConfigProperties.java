// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.codegen;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Encapsulates data extracted from system properties.
 *
 * @author gjoranv
 */
@SuppressWarnings("WeakerAccess") // Used by ConfigGenMojo
public class MakeConfigProperties {

    private static final List<String> legalLanguages = List.of("java", "cpp" );

    final File destDir;
    final List<File> specFiles;
    final String language;
    final String dirInRoot; // Where within fileroot to store generated class files
    final String javaPackagePrefix;
    final boolean dumpTree;
    final boolean generateFrameworkCode;

    MakeConfigProperties() throws PropertyException {
        this(System.getProperty("config.dest"),
             System.getProperty("config.spec"),
             System.getProperty("config.lang"),
             System.getProperty("config.subdir"),
             System.getProperty("config.dumpTree"),
             System.getProperty("config.useFramework"),
             System.getProperty("config.packagePrefix"));
    }

    @SuppressWarnings("WeakerAccess") // Used by ConfigGenMojo
    public MakeConfigProperties(String destDir,
                                String specFiles,
                                String language,
                                String dirInRoot,
                                String dumpTree,
                                String generateFrameworkCode,
                                String javaPackagePrefix) throws PropertyException {
        this.destDir = checkDestinationDir(destDir);
        this.specFiles = checkSpecificationFiles(specFiles);
        this.language = checkLanguage(language);
        this.dirInRoot = checkDirInRoot(this.destDir, dirInRoot);
        this.dumpTree = Boolean.parseBoolean(dumpTree);
        this.generateFrameworkCode = Boolean.parseBoolean(generateFrameworkCode);
        this.javaPackagePrefix = javaPackagePrefix;
    }

    private static File checkDestinationDir(String destination) throws PropertyException {
        if (destination == null)
            throw new PropertyException("Missing property: config.dest.");

        File dir = new File(destination);
        if (!dir.isDirectory()) {
            throw new PropertyException("Could not find directory: " + dir.getPath());
        }
        return dir;
    }

    private static String checkDirInRoot(File destDir,  String dirInRoot) throws PropertyException {
            // Optional parameter
        if (dirInRoot == null) { return null; }
        File f = new File(destDir, dirInRoot);
        if (!f.isDirectory()) {
            throw new PropertyException("Could not find directory: " + f.getPath());
        }
        return dirInRoot;
    }

    private static String checkLanguage(String lang) throws PropertyException {
        String inputLang = lang != null ? lang.toLowerCase() : "java";
        if (! legalLanguages.contains(inputLang)) {
            throw new PropertyException
                    ("Unsupported code language: '" + inputLang + "'. Supported languages are: " + legalLanguages);
        }
        return inputLang;
    }

    private static List<File> checkSpecificationFiles(String spec) throws PropertyException {
        if (spec == null || spec.isEmpty())
            throw new PropertyException("Missing property: config.spec");

        var files = new ArrayList<File>();
        for (String token : spec.split(",", -1)) {
            File file = new File(token);
            if (!file.isFile()) {
                throw new PropertyException("Could not read file " + file);
            }
            files.add(file);
        }

        return files;
    }

}

