// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    private final List<String> legalLanguages = Arrays.asList("java", "cpp", "cppng" );

    final File destDir;
    final File[] specFiles;
    final String language;
    final String dirInRoot; // Where within fileroot to store generated class files
    final boolean dumpTree;
    final boolean generateFrameworkCode;

    MakeConfigProperties() throws PropertyException {
        destDir = checkDestinationDir();
        specFiles = checkSpecificationFiles();
        language = checkLanguage();
        dirInRoot = checkDirInRoot();
        dumpTree = System.getProperty("config.dumpTree") != null &&
                   System.getProperty("config.dumpTree").equalsIgnoreCase("true");
        generateFrameworkCode = System.getProperty("config.useFramework") == null ||
                                System.getProperty("config.useFramework").equalsIgnoreCase("true");
    }

    private File checkDestinationDir() throws PropertyException {
        String destination = System.getProperty("config.dest");
        if (destination == null)
            throw new PropertyException("Missing property: config.dest.");

        File dir = new File(destination);
        if (!dir.isDirectory()) {
            throw new PropertyException("Could not find directory: " + dir.getPath());
        }
        return dir;
    }

    private String checkDirInRoot() throws PropertyException {
        String dirInRoot = System.getProperty("config.subdir");
            // Optional parameter
        if (dirInRoot == null) { return null; }
        File f = new File(destDir, dirInRoot);
        if (!f.isDirectory()) {
            throw new PropertyException("Could not find directory: " + f.getPath());
        }
        return dirInRoot;
    }

    /**
     * @return Desired programming language of generated code, default is "java".
     * @throws PropertyException if supplied language is not a legal language.
     */
    private String checkLanguage() throws PropertyException {
        String inputLang = System.getProperty("config.lang", "java").toLowerCase();
        if (! legalLanguages.contains(inputLang)) {
            throw new PropertyException
                    ("Unsupported code language: '" + inputLang + "'. Supported languages are: " + legalLanguages);
        }
        return inputLang;
    }

    private static File[] checkSpecificationFiles() throws PropertyException {
        String string = System.getProperty("config.spec");
        if (string == null)
            throw new PropertyException("Missing property: config.spec.");

        StringTokenizer st = new StringTokenizer(string, ",");
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

