// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.codegen;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Encapsulates data extracted from system properties.
 *
 * @author <a href="gv@yahoo-inc.com">Gjoran Voldengen</a>
 */
public class MakeConfigProperties {

    private static final List<String> legalLanguages = Arrays.asList("java", "cpp", "cppng" );

    final File destDir;
    final File[] specFiles;
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

    private static File[] checkSpecificationFiles(String spec) throws PropertyException {
        if (spec == null)
            throw new PropertyException("Missing property: config.spec.");

        StringTokenizer st = new StringTokenizer(spec, ",");
        if (st.countTokens() == 0)
            throw new PropertyException("Missing property: config.spec.");

        File[] files = new File[st.countTokens()];
        for (int i = 0; st.hasMoreElements(); i++) {
            files[i] = new File((String) st.nextElement());
            if (!files[i].isFile())
                throw new PropertyException("Could not read file " + files[i].getPath());
        }
        return files;
    }

}

