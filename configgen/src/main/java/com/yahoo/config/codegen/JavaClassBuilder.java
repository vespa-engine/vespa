// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.codegen;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yahoo.config.codegen.ConfigGenerator.indentCode;
import static com.yahoo.config.codegen.ConfiggenUtil.createClassName;
import static com.yahoo.config.codegen.DefParser.DEFAULT_PACKAGE_PREFIX;

/**
 * Builds one Java class based on the given CNode tree.
 *
 * @author gjoranv
 * @author Tony Vaagenes
 * @author ollivir
 */
public class JavaClassBuilder implements ClassBuilder {
    public static final String INDENTATION = "  ";

    private final InnerCNode root;
    private final NormalizedDefinition nd;
    private final String packagePrefix;
    private final String javaPackage;
    private final String className;
    private final File destDir;

    public JavaClassBuilder(InnerCNode root, NormalizedDefinition nd, File destDir, String rawPackagePrefix) {
        this.root = root;
        this.nd = nd;
        this.packagePrefix = (rawPackagePrefix != null) ? rawPackagePrefix : DEFAULT_PACKAGE_PREFIX;
        this.javaPackage = (root.getPackage() != null) ? root.getPackage() : packagePrefix + root.getNamespace();
        this.className = createClassName(root.getName());
        this.destDir = destDir;
    }

    @Override
    public void createConfigClasses() {
        try {
            File outFile = new File(getDestPath(destDir, javaPackage), className + ".java");
            try (PrintStream out = new PrintStream(new FileOutputStream(outFile))) {
                out.print(getConfigClass(className));
            }
            System.err.println(outFile.getPath() + " successfully written.");
        } catch (FileNotFoundException e) {
            throw new CodegenRuntimeException(e);
        }
    }

    public String getConfigClass(String className) {
        return getHeader() + "\n\n" + //
                getRootClassDeclaration(root, className) + "\n\n" + //
                indentCode(INDENTATION, getFrameworkCode()) + "\n\n" + //
                ConfigGenerator.generateContent(INDENTATION, root, true) + "\n" + //
                "}\n";
    }

    private String getHeader() {
        return "/**\n" + //
                " * This file is generated from a config definition file.\n" + //
                " * ------------   D O   N O T   E D I T !   ------------\n" + //
                " */\n" + //
                "\n" + //
                "package " + javaPackage + ";\n" + //
                "\n" + //
                "import java.util.*;\n" + //
                "import java.nio.file.Path;\n" + //
                "import edu.umd.cs.findbugs.annotations.NonNull;\n" + //
                getImportFrameworkClasses(root.getNamespace());
    }

    private String getImportFrameworkClasses(String namespace) {
        if (CNode.DEFAULT_NAMESPACE.equals(namespace) == false) {
            return "import " + packagePrefix + CNode.DEFAULT_NAMESPACE + ".*;";
        } else {
            return "";
        }
    }

    // TODO: remove the extra comment line " *" if root.getCommentBlock is empty
    private String getRootClassDeclaration(InnerCNode root, String className) {
        return "/**\n" + //
                " * This class represents the root node of " + root.getFullName() + "\n" + //
                " *\n" + //
                "" + root.getCommentBlock(" *") + " */\n" + //
                "public final class " + className + " extends ConfigInstance {\n" + //
                "\n" + //
                "  public final static String CONFIG_DEF_MD5 = \"" + root.getMd5() + "\";\n" + //
                "  public final static String CONFIG_DEF_NAME = \"" + root.getName() + "\";\n" + //
                "  public final static String CONFIG_DEF_NAMESPACE = \"" + root.getNamespace() + "\";\n" + //
                "  public final static String CONFIG_DEF_VERSION = \"" + root.getVersion() + "\";\n" + //
                "  public final static String[] CONFIG_DEF_SCHEMA = {\n" + //
                "" + indentCode(INDENTATION + INDENTATION, getDefSchema()) + "\n" + //
                "  };\n" + //
                "\n" + //
                "  public static String getDefMd5()       { return CONFIG_DEF_MD5; }\n" + //
                "  public static String getDefName()      { return CONFIG_DEF_NAME; }\n" + //
                "  public static String getDefNamespace() { return CONFIG_DEF_NAMESPACE; }\n" + //
                "  public static String getDefVersion()   { return CONFIG_DEF_VERSION; }";
    }

    private String getDefSchema() {
        return nd.getNormalizedContent().stream().map(l -> "\"" + l.replace("\"", "\\\"") + "\"").collect(Collectors.joining(",\n"));
    }

    private String getFrameworkCode() {
        return "public interface Producer extends ConfigInstance.Producer {\n" + //
                "  void getConfig(Builder builder);\n" + //
                "}";
    }

    /**
     * @param rootDir
     *            The root directory for the destination path.
     * @param javaPackage
     *            The java package
     * @return the destination path for the generated config file, including the
     *         given rootDir.
     */
    private File getDestPath(File rootDir, String javaPackage) {
        File dir = rootDir;
        for (String subDir : javaPackage.split("\\.")) {
            dir = new File(dir, subDir);
            synchronized (this) {
                if (!dir.isDirectory() && !dir.mkdir()) {
                    throw new CodegenRuntimeException("Could not create " + dir.getPath());
                }
            }
        }
        return dir;
    }

    /**
     * Returns a name that can be safely used as a local variable in the generated
     * config class for the given node. The name will be based on the given basis
     * string, but the basis itself is not a possible return value.
     *
     * @param node
     *            The node to find a unused symbol name for.
     * @param basis
     *            The basis for the generated symbol name.
     * @return A name that is not used in the given config node.
     */
    static String createUniqueSymbol(CNode node, String basis) {
        Set<String> usedSymbols = Arrays.stream(node.getChildren()).map(CNode::getName).collect(Collectors.toSet());
        Random rng = new Random();

        for (int i = 1;; i++) {
            String candidate = (i < basis.length()) ? basis.substring(0, i)
                    : ReservedWords.INTERNAL_PREFIX + basis + rng.nextInt(Integer.MAX_VALUE);
            if (usedSymbols.contains(candidate) == false) {
                return candidate;
            }
        }
    }

    public String className() {
        return className;
    }

    public String javaPackage() {
        return javaPackage;
    }
}
