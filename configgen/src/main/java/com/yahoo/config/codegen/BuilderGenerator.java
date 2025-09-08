// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.codegen;

import com.yahoo.config.codegen.LeafCNode.FileLeaf;
import com.yahoo.config.codegen.LeafCNode.ModelLeaf;
import com.yahoo.config.codegen.LeafCNode.PathLeaf;
import com.yahoo.config.codegen.LeafCNode.OptionalPathLeaf;
import com.yahoo.config.codegen.LeafCNode.UrlLeaf;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yahoo.config.codegen.ConfigGenerator.boxedDataType;
import static com.yahoo.config.codegen.ConfigGenerator.indentCode;
import static com.yahoo.config.codegen.ConfigGenerator.nodeClass;
import static com.yahoo.config.codegen.ConfigGenerator.userDataType;
import static com.yahoo.config.codegen.JavaClassBuilder.INDENTATION;
import static com.yahoo.config.codegen.JavaClassBuilder.createUniqueSymbol;
import static com.yahoo.config.codegen.ReservedWords.INTERNAL_PREFIX;
import static java.util.Arrays.stream;

/**
 * @author gjoranv
 * @author ollivir
 */
public class BuilderGenerator {

    public static String getBuilder(InnerCNode node) {
        return getDeclaration(node) + "\n" + //
                indentCode(INDENTATION, getUninitializedScalars(node) + "\n\n" + //
                        stream(node.getChildren()).map(BuilderGenerator::getBuilderFieldDefinition).collect(Collectors.joining("\n"))
                        + "\n\n" + //
                        getBuilderConstructors(node, nodeClass(node)) + "\n\n" + //
                        getOverrideMethod(node) + "\n\n" + //
                        getBuilderSetters(node) + "\n" + //
                        getSpecialRootBuilderCode(node) + "\n" + //
                        getBuildMethod(node) + "\n") //
                + "}";
    }

    private static String getDeclaration(InnerCNode node) {
        String getInterfaces = (node.getParent() == null) ? "implements ConfigInstance.Builder" : "implements ConfigBuilder";

        return "public static final class Builder " + getInterfaces + " {";
    }

    private static String getSpecialRootBuilderCode(InnerCNode node) {
        return (node.getParent() == null) ? "\n" + getRootDeclarations() + "\n" : "";
    }

    private static String getBuildMethod(InnerCNode node) {
        return "public " + nodeClass(node) + " build() {\n" +
               "  return new " + nodeClass(node) + "(this);\n" +
               "}\n";
    }

    private static String getRootDeclarations() {
        // Use full path to @Override, as users are free to define an inner node called
        // 'override'. (summarymap.def does)
        // The generated inner 'Override' class would otherwise be mistaken for the
        // annotation.
        return  "private boolean _applyOnRestart = false;\n" +
                "\n" +
                "@java.lang.Override\n" +
                "public final boolean dispatchGetConfig(ConfigInstance.Producer producer) {\n" +
                "  if (producer instanceof Producer) {\n" +
                "    ((Producer)producer).getConfig(this);\n" +
                "    return true;\n" +
                "  }\n" + //
                "  return false;\n" +
                "}\n" +
                "\n" +
                "@java.lang.Override\n" +
                "public final String getDefMd5() { return CONFIG_DEF_MD5; }\n" +
                "\n" +
                "@java.lang.Override\n" +
                "public final String getDefName() { return CONFIG_DEF_NAME; }\n" +
                "\n" +
                "@java.lang.Override\n" +
                "public final String getDefNamespace() { return CONFIG_DEF_NAMESPACE; }\n" +
                "\n" +
                "@java.lang.Override\n" +
                "public final boolean getApplyOnRestart() { return _applyOnRestart; }\n" +
                "\n" +
                "@java.lang.Override\n" +
                "public final void setApplyOnRestart(boolean applyOnRestart) { _applyOnRestart = applyOnRestart; }";
    }

    private static String getUninitializedScalars(InnerCNode node) {
        List<String> scalarsWithoutDefault = new ArrayList<>();
        for (CNode child : node.getChildren()) {
            if (child instanceof LeafCNode
                    && (!child.isArray && !child.isMap && ((LeafCNode) child).getDefaultValue() == null)
                    && (! (child instanceof OptionalPathLeaf))) {
                scalarsWithoutDefault.add("\"" + child.getName() + "\"");
            }
        }

        String uninitializedList = (scalarsWithoutDefault.size() > 0)
                ? "List.of(\n" + indentCode(INDENTATION, String.join(",\n", scalarsWithoutDefault) + "\n)")
                : "";

        return "private Set<String> " + INTERNAL_PREFIX + "uninitialized = new HashSet<String>(" + uninitializedList + ");";
    }

    private static String getBuilderFieldDefinition(CNode node) {
        if (node.isArray) {
            return String.format("public List<%s> %s = new ArrayList<>();", builderType(node), node.getName());
        } else if (node.isMap) {
            return String.format("public Map<String, %s> %s = new LinkedHashMap<>();", builderType(node), node.getName());
        } else if (node instanceof InnerCNode) {
            return String.format("public %s %s = new %s();", builderType(node), node.getName(), builderType(node));
        } else if (node instanceof LeafCNode) {
            String boxedBuilderType = boxedBuilderType((LeafCNode) node);
            if (boxedBuilderType.startsWith("Optional<"))
                return String.format("private %s %s = Optional.empty();", boxedBuilderType, node.getName());
            else
                return String.format("private %s %s = null;", boxedBuilderType, node.getName());
        } else {
            throw new IllegalStateException("Cannot produce builder field definition for node"); // Should not happen
        }
    }

    private static String getBuilderSetters(CNode node) {
        List<String> elem = new ArrayList<>();
        CNode[] children = node.getChildren();

        for (CNode child : children) {
            if (child instanceof InnerCNode && child.isArray) {
                elem.add(BuilderSetters.innerArraySetters((InnerCNode) child));
            } else if (child instanceof InnerCNode && child.isMap) {
                elem.add(BuilderSetters.innerMapSetters(child));
            } else if (child instanceof LeafCNode && child.isArray) {
                elem.add(BuilderSetters.leafArraySetters((LeafCNode) child));
            } else if (child instanceof LeafCNode && child.isMap) {
                elem.add(BuilderSetters.leafMapSetters(child));
            } else if (child instanceof InnerCNode) {
                elem.add(BuilderSetters.structSetter((InnerCNode) child));
            } else if (child instanceof LeafCNode) {
                elem.add(BuilderSetters.scalarSetters((LeafCNode) child));
            }
        }
        return String.join("\n\n", elem);
    }

    private static class BuilderSetters {

        private static String structSetter(InnerCNode n) {
            return "public Builder " + n.getName() + "(" + builderType(n) + " " + INTERNAL_PREFIX + "builder) {\n" + //
                    "  " + n.getName() + " = " + INTERNAL_PREFIX + "builder;\n" + //
                    "  return this;\n" + //
                    "}\n" + //
                    "/**\n" + //
                    " * Make a new builder and run the supplied function on it before adding it to the list\n" + //
                    " * @param __func lambda that modifies the given builder\n" + //
                    " * @return this builder\n" + //
                    " */\n" + //
                    "public Builder " + n.getName() + "(java.util.function.Consumer<" + builderType(n) + "> __func) {\n" + //
                    "  " + builderType(n) + " __inner = new " + builderType(n) +"();\n" + //
                    "  __func.accept(__inner);\n" + //
                    "  " + n.getName() + " = __inner;\n" + //
                    "  return this;\n" + //
                    "}";

        }

        private static String innerArraySetters(InnerCNode n) {
            return "/**\n" + //
                    " * Add the given builder to this builder's list of " + nodeClass(n) + " builders\n" + //
                    " * @param " + INTERNAL_PREFIX + "builder a builder\n" + //
                    " * @return this builder\n" + //
                    " */\n" + //
                    "public Builder " + n.getName() + "(" + builderType(n) + " " + INTERNAL_PREFIX + "builder) {\n" + //
                    "  " + n.getName() + ".add(" + INTERNAL_PREFIX + "builder);\n" + //
                    "  return this;\n" + //
                    "}\n" + //
                    "\n" + //
                    "/**\n" + //
                    " * Make a new builder and run the supplied function on it before adding it to the list\n" + //
                    " * @param __func lambda that modifies the given builder\n" + //
                    " * @return this builder\n" + //
                    " */\n" + //
                    "public Builder " + n.getName() + "(java.util.function.Consumer<" + builderType(n) + "> __func) {\n" + //
                    "  " + builderType(n) + " __inner = new " + builderType(n) +"();\n" + //
                    "  __func.accept(__inner);\n" + //
                    "  " + n.getName() + ".add(__inner);\n" + //
                    "  return this;\n" + //
                    "}\n" + //
                    "\n" + //
                    "/**\n" + //
                    " * Set the given list as this builder's list of " + nodeClass(n) + " builders\n" + //
                    " * @param __builders a list of builders\n" + //
                    " * @return this builder\n" + //
                    " */\n" + //
                    "public Builder " + n.getName() + "(List<" + builderType(n) + "> __builders) {\n" + //
                    "  " + n.getName() + " = __builders;\n" + //
                    "  return this;\n" + //
                    "}";
        }

        private static String publicLeafNodeSetters(LeafCNode n) {
            return "public Builder " + n.getName() + "(" + builderType(n) + " " + INTERNAL_PREFIX + "value) {\n" + //
                    "  " + n.getName() + ".add(" + INTERNAL_PREFIX + "value);\n" + //
                    "  return this;\n" + //
                    "}\n" + //
                    "\n" + //
                    "public Builder " + n.getName() + "(Collection<" + builderType(n) + "> " + INTERNAL_PREFIX + "values) {\n" + //
                    "  " + n.getName() + ".addAll(" + INTERNAL_PREFIX + "values);\n" + //
                    "  return this;\n" + //
                    "}";
        }

        private static String privateLeafNodeSetter(LeafCNode n) {
            if ("String".equals(builderType(n)) || "FileReference".equals(builderType(n))) {
                return "";
            } else if ("Optional<FileReference>".equals(builderType(n))) {
                return "\n\n" + //
                        "private Builder " + n.getName() + "(String " + INTERNAL_PREFIX + "value) {\n" + //
                        "  return " + n.getName() + "(" + builderType(n) + ".of(" + INTERNAL_PREFIX + "value));\n" + //
                        "}";
            } else {
                return "\n\n" + //
                        "private Builder " + n.getName() + "(String " + INTERNAL_PREFIX + "value) {\n" + //
                        "  return " + n.getName() + "(" + builderType(n) + ".valueOf(" + INTERNAL_PREFIX + "value));\n" + //
                        "}";
            }
        }

        private static String leafArraySetters(LeafCNode n) {
            return publicLeafNodeSetters(n) + privateLeafNodeSetter(n);
        }

        private static String innerMapSetters(CNode n) {
            String r = "public Builder " + n.getName() + "(String " + INTERNAL_PREFIX + "key, " + builderType(n) + " " + INTERNAL_PREFIX + "value) {\n" + //
                    "  " + n.getName() + ".put(" + INTERNAL_PREFIX + "key, " + INTERNAL_PREFIX + "value);\n" + //
                    "  return this;\n" + //
                    "}\n" + //
                    "\n" + //
                    "public Builder " + n.getName() + "(Map<String, " + builderType(n) + "> " + INTERNAL_PREFIX + "values) {\n" + //
                    "  " + n.getName() + ".putAll(" + INTERNAL_PREFIX + "values);\n" + //
                    "  return this;\n" + //
                    "}";
            if (n instanceof InnerCNode) {
                r = r +
                    "\n\n" + //
                    "/**\n" + //
                    " * Make a new builder and run the supplied function on it before using it as the value\n" + //
                    " * @param __func lambda that modifies the given builder\n" + //
                    " * @return this builder\n" + //
                    " */\n" + //
                    "public Builder " + n.getName() + "(String __key, java.util.function.Consumer<" + builderType(n) + "> __func) {\n" + //
                    "  " + builderType(n) + " __inner = new " + builderType(n) +"();\n" + //
                    "  __func.accept(__inner);\n" + //
                    "  " + n.getName() + ".put(__key, __inner);\n" + //
                    "  return this;\n" + //
                    "}";
            }
            return r;
        }

        private static String privateLeafMapSetter(CNode n) {
            if ("String".equals(builderType(n)) || "FileReference".equals(builderType(n))) {
                return "";
            } else {
                return "\n\n" + //
                        "private Builder " + n.getName() + "(String " + INTERNAL_PREFIX + "key, String " + INTERNAL_PREFIX + "value) {\n" + //
                        "  return " + n.getName() + "(" + INTERNAL_PREFIX + "key, " + builderType(n) + ".valueOf(" + INTERNAL_PREFIX
                        + "value));\n" + //
                        "}";
            }
        }

        private static String leafMapSetters(CNode n) {
            return innerMapSetters(n) + privateLeafMapSetter(n);
        }

        private static String scalarSetters(LeafCNode n) {
            String name = n.getName();

            String signalInitialized = (n.getDefaultValue() == null) ? "  " + INTERNAL_PREFIX + "uninitialized.remove(\"" + name + "\");\n"
                    : "";

            String bType = builderType(n);
            String privateSetter = "";
            if ( ! Set.of("String", "FileReference", "ModelReference", "Optional<FileReference>").contains(bType)) {
                String type = boxedDataType(n);
                if ("UrlReference".equals(bType))
                    type = bType;
                //
                System.err.println(String.format("gen privateSetter for '%s' bType '%s'", name, bType));
                privateSetter = String.format("""

                                                      private Builder %s(String %svalue) {
                                                        return %s(%s.valueOf(%svalue));
                                                      }""", name, INTERNAL_PREFIX, name, type, INTERNAL_PREFIX);
            } else if ("Optional<FileReference>".equals(bType)) {
                //
                privateSetter = String.format("""

                                                      private Builder %s(FileReference %svalue) {
                                                        return %s(Optional.of(%svalue));
                                                      }""", name, INTERNAL_PREFIX, name, INTERNAL_PREFIX);
            }

            String getNullGuard = bType.equals(boxedBuilderType(n)) ? String.format(
                    "\nif (%svalue == null) throw new IllegalArgumentException(\"Null value is not allowed.\");", INTERNAL_PREFIX) : "";

            return String.format("public Builder %s(%s %svalue) {%s\n" +
                    "  %s = %svalue;\n" + //
                    "%s", name, bType, INTERNAL_PREFIX, getNullGuard, name, INTERNAL_PREFIX, signalInitialized) +
                    "  return this;" + "\n}\n" + privateSetter;
        }
    }

    private static String setBuilderValueFromConfig(CNode child, CNode node) {
        String name = child.getName();
        boolean isArray = child.isArray;
        boolean isMap = child.isMap;

        if (child instanceof FileLeaf && isArray) {
            return name + "(" + userDataType(child) + ".toValues(config." + name + "()));";
        } else if (child instanceof FileLeaf && isMap) {
            return name + "(" + userDataType(child) + ".toValueMap(config." + name + "()));";
        } else if (child instanceof FileLeaf) {
            return name + "(config." + name + "().value());";
        } else if (child instanceof PathLeaf && isArray) {
            return name + "(" + nodeClass(child) + ".toFileReferences(config." + name + "));";
        } else if (child instanceof PathLeaf && isMap) {
            return name + "(" + nodeClass(child) + ".toFileReferenceMap(config." + name + "));";
        } else if (child instanceof PathLeaf) {
            return name + "(config." + name + ".getFileReference());";
        } else if (child instanceof OptionalPathLeaf) {
            return name + "(config." + name + ".getFileReference());";
        } else if (child instanceof UrlLeaf && isArray) {
            return name + "(" + nodeClass(child) + ".toUrlReferences(config." + name + "));";
        } else if (child instanceof UrlLeaf && isMap) {
            return name + "(" + nodeClass(child) + ".toUrlReferenceMap(config." + name + "));";
        } else if (child instanceof UrlLeaf) {
            return name + "(config." + name + ".getUrlReference());";
        } else if (child instanceof ModelLeaf && isArray) {
            return name + "(" + nodeClass(child) + ".toModelReferences(config." + name + "));";
        } else if (child instanceof ModelLeaf && isMap) {
            return name + "(" + nodeClass(child) + ".toModelReferenceMap(config." + name + "));";
        } else if (child instanceof ModelLeaf) {
            return name + "(config." + name + ".getModelReference());";
        } else if (child instanceof LeafCNode) {
            return name + "(config." + name + "());";
        } else if (child instanceof InnerCNode && isArray) {
            return setInnerArrayBuildersFromConfig((InnerCNode) child, node);
        } else if (child instanceof InnerCNode && isMap) {
            return setInnerMapBuildersFromConfig((InnerCNode) child);
        } else {
            return name + "(new " + builderType(child) + "(config." + name + "()));";
        }
    }

    private static String setInnerArrayBuildersFromConfig(InnerCNode innerArr, CNode node) {
        String elemName = createUniqueSymbol(node, innerArr.getName());

        return "for (" + userDataType(innerArr) + " " + elemName + " : config." + innerArr.getName() + "()) {\n" + //
                "  " + innerArr.getName() + "(new " + builderType(innerArr) + "(" + elemName + "));\n" + //
                "}";
    }

    private static String setInnerMapBuildersFromConfig(InnerCNode innerMap) {
        String entryName = INTERNAL_PREFIX + "entry";
        return "for (Map.Entry<String, " + userDataType(innerMap) + "> " + entryName + " : config." + innerMap.getName()
                + "().entrySet()) {\n" + //
                "  " + innerMap.getName() + "(" + entryName + ".getKey(), new " + userDataType(innerMap) + ".Builder(" + entryName
                + ".getValue()));\n" + //
                "}";
    }

    private static String getBuilderConstructors(CNode node, String className) {
        return "public Builder() { }\n" + //
                "\n" + //
                "public Builder(" + className + " config) {\n" + //
                indentCode(INDENTATION,
                        stream(node.getChildren()).map(child -> setBuilderValueFromConfig(child, node)).collect(Collectors.joining("\n")))
                + //
                "\n}";
    }

    private static String conditionStatement(CNode child) {
        String superior = INTERNAL_PREFIX + "superior";

        if (child.isArray) {
            return "if (!" + superior + "." + child.getName() + ".isEmpty())";
        } else if (child.isMap) {
            return "";
        } else if (child instanceof LeafCNode) {
            return "if (" + superior + "." + child.getName() + " != null)";
        } else {
            return "";
        }
    }

    private static String overrideBuilderValue(CNode child) {
        final String superior = INTERNAL_PREFIX + "superior";
        final String method = "override";
        final String name = child.getName();
        final String callSetter = name + "(" + superior + "." + name + ");";

        if (child.isArray) {
            String arrayOverride = INDENTATION + name + ".addAll(" + superior + "." + name + ");";
            return conditionStatement(child) + "\n" + arrayOverride;
        } else if (child instanceof InnerCNode && !child.isArray && !child.isMap) {
            return name + "(" + name + "." + method + "(" + superior + "." + name + "));";
        } else if (child.isMap) {
            return callSetter;
        } else {
            return conditionStatement(child) + "\n" + INDENTATION + callSetter;
        }
    }

    private static String getOverrideMethod(CNode node) {
        String superior = INTERNAL_PREFIX + "superior";
        String method = "override";

        return "private Builder " + method + "(Builder " + superior + ") {\n" + //
                indentCode(INDENTATION,
                        stream(node.getChildren()).map(BuilderGenerator::overrideBuilderValue).collect(Collectors.joining("\n")))
                + "\n" + //
                "  return this;\n" + //
                "}";
    }

    private static String builderType(CNode node) {
        if (node instanceof InnerCNode) {
            return boxedDataType(node) + ".Builder";
        } else if (node instanceof FileLeaf) {
            return "String";
        } else if (node instanceof PathLeaf) {
            return "FileReference";
        } else if (node instanceof OptionalPathLeaf) {
            return "Optional<FileReference>";
        } else if (node instanceof UrlLeaf) {
            return "UrlReference";
        } else if (node instanceof ModelLeaf) {
            return "ModelReference";
        } else if (node instanceof LeafCNode && (node.isArray || node.isMap)) {
            return boxedDataType(node);
        } else {
            return userDataType(node);
        }
    }

    private static String boxedBuilderType(LeafCNode node) {
        if (node instanceof FileLeaf) {
            return "String";
        } else if (node instanceof PathLeaf) {
            return "FileReference";
        } else if (node instanceof OptionalPathLeaf) {
            return "Optional<FileReference>";
        } else if (node instanceof UrlLeaf) {
            return "UrlReference";
        } else if (node instanceof ModelLeaf) {
            return "ModelReference";
        } else {
            return boxedDataType(node);
        }
    }

}
