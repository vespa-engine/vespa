// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.codegen;

import java.io.*;
import java.util.logging.Logger;

/**
 * This class generates code for a config class from a given def-file.
 */
public class MakeConfig {

    private final static Logger log = Logger.getLogger(MakeConfig.class.getName());

    private final ClassBuilder classBuilder;

    public MakeConfig(InnerCNode root, NormalizedDefinition nd, String path, MakeConfigProperties properties) {
        classBuilder = createClassBuilder(root, nd, path, properties);
    }

    public static ClassBuilder createClassBuilder(InnerCNode root, NormalizedDefinition nd, String path, MakeConfigProperties prop) {
        if (prop.language.equals("cppng") || prop.language.equals("cpp"))
            return new CppClassBuilder(root, nd, prop.destDir, prop.dirInRoot);
        else
            return new JavaClassBuilder(root, nd, prop.destDir);
    }

    /**
     * Generates the code and print it to this.out.
     */
    void buildClasses() {
        classBuilder.createConfigClasses();
    }

    private static void printUsage(PrintStream out) {
        out.println("Usage: java -Dconfig.dest=<dir> -Dconfig.spec=<path> [-Dconfig.lang=cpp -Dconfig.subdir=<dir>] [-Dconfig.dumpTree=true] MakeConfig");
        out.println("       (default language for generated code is Java)");
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        try {
            MakeConfigProperties props = new MakeConfigProperties();
            for (File specFile : props.specFiles) {
                String path = specFile.toURI().toString();
                String name = specFile.getName();
                if (name.endsWith(".def")) name = name.substring(0, name.length() - 4);
                DefParser parser = new DefParser(name, new FileReader(specFile));
                InnerCNode configRoot = parser.getTree();
                checkNamespace(name, configRoot);
                if (configRoot != null) {
                    MakeConfig mc = new MakeConfig(configRoot, parser.getNormalizedDefinition(), path, props);
                    mc.buildClasses();
                    if (props.dumpTree) {
                        System.out.println("\nTree dump:");
                        DefParser.dumpTree(configRoot, "");
                    }
                } else {
                    System.exit(1);
                }
            }
        } catch (PropertyException e) {
            System.out.println(Exceptions.toMessageString(e));
            printUsage(System.err);
            System.exit(1);
        } catch (CodegenRuntimeException e) {
            System.out.println(Exceptions.toMessageString(e));
            System.exit(1);
        }
    }

    private static void checkNamespace(String name, InnerCNode configRoot) {
        if (configRoot.defNamespace == null)
            throw new IllegalArgumentException("In config definition '" + name + "': A namespace is required");
    }

    // The Exceptions class below is copied from vespajlib/com.yahoo.protect.Exceptions

    /**
     * Helper methods for handling exceptions
     *
     * @author bratseth
     */
    static class Exceptions {

        /**
         * <p>Returns a use friendly error message string which includes information from all nested exceptions.
         *
         * <p>The form of this string is
         * <code>e.getMessage(): e.getCause().getMessage(): e.getCause().getCause().getMessage()...</code>
         * In addition, some heuristics are used to clean up common cases where exception nesting causes bad messages.
         */
        public static String toMessageString(Throwable t) {
            StringBuilder b = new StringBuilder();
            String lastMessage = null;
            String message;
            for (; t != null; t = t.getCause(), lastMessage = message) {
                message = getMessage(t);
                if (message == null) continue;
                if (lastMessage != null && lastMessage.equals(message)) continue;
                if (b.length() > 0)
                    b.append(": ");
                b.append(message);
            }
            return b.toString();
        }

        /**
         * Returns a useful message from *this* exception, or null if none
         */
        private static String getMessage(Throwable t) {
            String message = t.getMessage();
            if (t.getCause() == null) {
                if (message == null) return toShortClassName(t);
                return message;
            } else {
                if (message == null) return null;
                if (message.equals(t.getCause().getClass().getName() + ": " + t.getCause().getMessage())) return null;
                return message;
            }
        }

        private static String toShortClassName(Object o) {
            String longName = o.getClass().getName();
            int lastDot = longName.lastIndexOf(".");
            if (lastDot < 0) return longName;
            return longName.substring(lastDot + 1);
        }
    }
}

