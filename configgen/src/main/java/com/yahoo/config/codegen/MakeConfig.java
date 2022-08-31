// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.codegen;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;

/**
 * This class generates code for a config class from a given def-file.
 */
public class MakeConfig {

    private final ClassBuilder classBuilder;

    public MakeConfig(InnerCNode root, NormalizedDefinition nd, MakeConfigProperties properties) {
        classBuilder = createClassBuilder(root, nd, properties);
    }

    private static ClassBuilder createClassBuilder(InnerCNode root, NormalizedDefinition nd, MakeConfigProperties properties) {
        if (isCpp(properties))
            return new CppClassBuilder(root, nd, properties.destDir, properties.dirInRoot);
        else
            return new JavaClassBuilder(root, nd, properties.destDir, properties.javaPackagePrefix);
    }

    @SuppressWarnings("WeakerAccess") // Used by ConfigGenMojo
    public static boolean makeConfig(MakeConfigProperties properties) throws FileNotFoundException {
        for (File specFile : properties.specFiles) {
            String name = specFile.getName();
            if (name.endsWith(".def")) name = name.substring(0, name.length() - 4);

            DefParser parser = new DefParser(name, new FileReader(specFile));
            parser.enableSystemErr();
            InnerCNode configRoot = parser.getTree();
            checkNamespaceAndPacakge(name, configRoot, isCpp(properties));

            if (configRoot != null) {
                MakeConfig mc = new MakeConfig(configRoot, parser.getNormalizedDefinition(), properties);
                mc.buildClasses();
                if (properties.dumpTree) {
                    System.out.println("\nTree dump:");
                    DefParser.dumpTree(configRoot, "");
                }
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * Generates the code and print it to this.out.
     */
    private void buildClasses() {
        classBuilder.createConfigClasses();
    }

    private static void printUsage(PrintStream out) {
        out.println("Usage: java -Dconfig.dest=<dir> -Dconfig.spec=<path> [-Dconfig.lang=cpp -Dconfig.subdir=<dir>] [-Dconfig.dumpTree=true] MakeConfig");
        out.println("       (default language for generated code is Java)");
    }

    public static void main(String[] args) throws IOException {
        try {
            MakeConfigProperties props = new MakeConfigProperties();
            boolean success = makeConfig(props);
            if (!success) System.exit(1);
        } catch (PropertyException e) {
            System.out.println(Exceptions.toMessageString(e));
            printUsage(System.err);
            System.exit(1);
        } catch (CodegenRuntimeException e) {
            System.out.println(Exceptions.toMessageString(e));
            System.exit(1);
        }
    }

   private static void checkNamespaceAndPacakge(String name, InnerCNode configRoot, boolean isCpp) {
        if (isCpp && configRoot.defNamespace == null)
            throw new IllegalArgumentException("In config definition '" + name + "': A namespace is required");
       if (configRoot.defNamespace == null && configRoot.defPackage == null)
           throw new IllegalArgumentException("In config definition '" + name + "': A package (or namespace) is required");
    }

    private static boolean isCpp(MakeConfigProperties properties) {
        return properties.language.equals("cpp");
    }

    // The Exceptions class below is copied from vespajlib/com.yahoo.protect.Exceptions

    /**
     * Helper methods for handling exceptions
     *
     * @author bratseth
     */
    static class Exceptions {

        /**
         * Returns a use friendly error message string which includes information from all nested exceptions.
         *
         * The form of this string is
         * <code>e.getMessage(): e.getCause().getMessage(): e.getCause().getCause().getMessage()...</code>
         * In addition, some heuristics are used to clean up common cases where exception nesting causes bad messages.
         */
        static String toMessageString(Throwable t) {
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

