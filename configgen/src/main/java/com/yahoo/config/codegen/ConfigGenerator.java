// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.codegen;

import com.yahoo.config.codegen.LeafCNode.BooleanLeaf;
import com.yahoo.config.codegen.LeafCNode.DoubleLeaf;
import com.yahoo.config.codegen.LeafCNode.EnumLeaf;
import com.yahoo.config.codegen.LeafCNode.FileLeaf;
import com.yahoo.config.codegen.LeafCNode.IntegerLeaf;
import com.yahoo.config.codegen.LeafCNode.LongLeaf;
import com.yahoo.config.codegen.LeafCNode.PathLeaf;
import com.yahoo.config.codegen.LeafCNode.ReferenceLeaf;
import com.yahoo.config.codegen.LeafCNode.StringLeaf;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import static com.yahoo.config.codegen.BuilderGenerator.getBuilder;
import static com.yahoo.config.codegen.JavaClassBuilder.INDENTATION;
import static com.yahoo.config.codegen.ReservedWords.INTERNAL_PREFIX;
import static java.util.Arrays.stream;

/**
 * @author gjoranv
 * @author Tony Vaagenes
 * @author ollivir
 */
public class ConfigGenerator {
    // TODO: don't take indent as method param - the caller should indent
    public static String generateContent(String indent, InnerCNode node, boolean isOuter) {
        CNode[] children = node.getChildren();

        return indentCode(indent,
                getBuilder(node) + "\n\n" +
                        stream(children).map(ConfigGenerator::getFieldDefinition).collect(Collectors.joining("\n")) + "\n\n" +
                        getConstructors(node) + "\n\n" +
                        getAccessors(children) + "\n\n" +
                        getGetChangesRequiringRestart(node) + "\n\n" +
                        getContainsFieldsFlaggedWithRestart(node, isOuter) +
                        getStaticMethods(node) +
                        generateCodeForChildren(children, indent)
        );
    }

    private static String generateCodeForChildren(CNode[] children, String indent) {
        List<String> pieces = new LinkedList<>();
        for (CNode child : children) {
            if (child instanceof EnumLeaf) {
                pieces.add(getEnumCode((EnumLeaf) child) + "\n");
            } else if (child instanceof InnerCNode) {
                pieces.add(getInnerDefinition((InnerCNode) child, indent) + "\n");
            }
        }
        return String.join("\n", pieces);
    }

    private static String getInnerDefinition(InnerCNode inner, String indent) {
        return (getClassDoc(inner) + "\n" +//
                getClassDeclaration(inner) + "\n" +//
                generateContent(indent, inner, false)).trim() + "\n}";
    }

    private static String getClassDeclaration(CNode node) {
        return "public final static class " + nodeClass(node) + " extends InnerNode { \n";
    }

    private static String getFieldDefinition(CNode node) {
        String fieldDef;
        if (node instanceof LeafCNode && node.isArray) {
            fieldDef = String.format("LeafNodeVector<%s, %s> %s;", boxedDataType(node), nodeClass(node), node.getName());
        } else if (node instanceof InnerCNode && node.isArray) {
            fieldDef = String.format("InnerNodeVector<%s> %s;", nodeClass(node), node.getName());
        } else if (node.isMap) {
            fieldDef = String.format("Map<String, %s> %s;", nodeClass(node), node.getName());
        } else {
            fieldDef = String.format("%s %s;", nodeClass(node), node.getName());
        }
        return node.getCommentBlock("//") + "private final " + fieldDef;
    }

    private static String getStaticMethods(InnerCNode node) {
        if (node.isArray) {
            return getStaticMethodsForInnerArray(node) + "\n\n";
        } else if (node.isMap) {
            return getStaticMethodsForInnerMap(node) + "\n\n";
        } else {
            return "";
        }
    }

    private static String getContainsFieldsFlaggedWithRestart(CNode node, boolean isOuter) {
        if (isOuter) {
            return String.format("private static boolean containsFieldsFlaggedWithRestart() {\n" +//
                    "  return %b;\n" +//
                    "}\n\n", node.needRestart());
        } else {
            return "";
        }
    }

    private static String getGetChangesRequiringRestart(InnerCNode node) {
        List<String> comparisons = new LinkedList<>();
        for (CNode child : node.getChildren()) {
            if (child.needRestart()) {
                comparisons.add("\n  " + getComparison(child));
            }
        }

        return "private ChangesRequiringRestart getChangesRequiringRestart(" + nodeClass(node) + " newConfig) {\n" +//
                "  ChangesRequiringRestart changes = new ChangesRequiringRestart(\"" + node.getName() + "\");" + String.join("", comparisons) + "\n" +//
                "  return changes;\n" +//
                "}";
    }

    private static String quotedComment(CNode node) {
        return node.getComment().replace("\n", "\\n").replace("\"", "\\\"");
    }

    private static String getComparison(CNode node) {
        if (node instanceof InnerCNode && node.isArray) {
            return "  changes.compareArray(this." + node.getName() + ", newConfig." + node.getName() + ", \"" + node.getName() + "\", \"" + quotedComment(node) + "\",\n" +//
                    "                      (a,b) -> ((" + nodeClass(node) + ")a).getChangesRequiringRestart((" + nodeClass(node) + ")b));";
        } else if (node instanceof InnerCNode && node.isMap) {
            return "  changes.compareMap(this." + node.getName() + ", newConfig." + node.getName() + ", \"" + node.getName() + "\", \"" + quotedComment(node) + "\",\n" +//
                    "                    (a,b) -> ((" + nodeClass(node) + ")a).getChangesRequiringRestart((" + nodeClass(node) + ")b));";
        } else if (node instanceof InnerCNode) {
            return "  changes.mergeChanges(\"" + node.getName() + "\", this." + node.getName() + ".getChangesRequiringRestart(newConfig." + node.getName() + "));";
        } else if (node.isArray) {
            return "  changes.compareArray(this." + node.getName() + ", newConfig." + node.getName() + ", \"" + node.getName() + "\", \"" + quotedComment(node) + "\",\n" +//
                    "                      (a,b) -> new ChangesRequiringRestart(\"" + node.getName() + "\").compare(a,b,\"\",\"" + quotedComment(node) + "\"));";
        } else if (node.isMap) {
            return "  changes.compareMap(this." + node.getName() + ", newConfig." + node.getName() + ", \"" + node.getName() + "\", \"" + quotedComment(node) + "\",\n" +//
                    "                    (a,b) -> new ChangesRequiringRestart(\"" + node.getName() + "\").compare(a,b,\"\",\"" + quotedComment(node) + "\"));";
        } else {
            return "  changes.compare(this." + node.getName() + ", newConfig." + node.getName() + ", \"" + node.getName() + "\", \"" + quotedComment(node) + "\");";
        }
    }

    private static String scalarDefault(LeafCNode scalar) {
        if (scalar.getDefaultValue() == null) {
            return "";
        } else if (scalar instanceof EnumLeaf && scalar.getDefaultValue().getValue() == null) {
            return "";
        } else if (scalar instanceof EnumLeaf) {
            return nodeClass(scalar) + "." + scalar.getDefaultValue().getStringRepresentation();
        } else if (scalar instanceof LongLeaf) {
            return scalar.getDefaultValue().getStringRepresentation() + "L";
        } else if (scalar instanceof DoubleLeaf) {
            return scalar.getDefaultValue().getStringRepresentation() + "D";
        } else {
            return scalar.getDefaultValue().getStringRepresentation();
        }
    }

    private static String assignFromBuilder(CNode child) {
        final String name = child.getName();
        final String className = nodeClass(child);
        final boolean isArray = child.isArray;
        final boolean isMap = child.isMap;

        if (child instanceof FileLeaf && isArray) {
            return name + " = LeafNodeVector.createFileNodeVector(builder." + name + ");";
        } else if (child instanceof PathLeaf && isArray) {
            return name + " = LeafNodeVector.createPathNodeVector(builder." + name + ");";
        } else if (child instanceof LeafCNode && isArray) {
            return name + " = new LeafNodeVector<>(builder." + name + ", new " + className + "());";
        } else if (child instanceof FileLeaf && isMap) {
            return name + " = LeafNodeMaps.asFileNodeMap(builder." + name + ");";
        } else if (child instanceof PathLeaf && isMap) {
            return name + " = LeafNodeMaps.asPathNodeMap(builder." + name + ");";
        } else if (child instanceof LeafCNode && isMap) {
            return name + " = LeafNodeMaps.asNodeMap(builder." + name + ", new " + className + "());";
        } else if (child instanceof InnerCNode && isArray) {
            return name + " = " + className + ".createVector(builder." + name + ");";
        } else if (child instanceof InnerCNode && isMap) {
            return name + " = " + className + ".createMap(builder." + name + ");";
        } else if (child instanceof InnerCNode) {
            return name + " = new " + className + "(builder." + name + ", throwIfUninitialized);";
        } else if (child instanceof LeafCNode) {
            return name + " = (builder." + name + " == null) ?\n" +//
                    "    new " + className + "(" + scalarDefault((LeafCNode) child) + ") : new " + className + "(builder." + name + ");";
        } else {
            throw new IllegalStateException("Cannot create assignment for node"); // should not happen
        }
    }

    private static String getConstructors(InnerCNode inner) {
        // TODO: merge these two constructors into one when the config library uses builders to set values from payload.
        return "public " + nodeClass(inner) + "(Builder builder) {\n" +//
                "  this(builder, true);\n" +//
                "}\n" +//
                "\n" +//
                "private " + nodeClass(inner) + "(Builder builder, boolean throwIfUninitialized) {\n" +//
                "  if (throwIfUninitialized && ! builder." + INTERNAL_PREFIX + "uninitialized.isEmpty())\n" +//
                "    throw new IllegalArgumentException(\"The following builder parameters for \" +\n" +//
                "        \"" + inner.getFullName() + " must be initialized: \" + builder." + INTERNAL_PREFIX + "uninitialized);\n" +//
                "\n" +//
                indentCode(INDENTATION, stream(inner.getChildren()).map(ConfigGenerator::assignFromBuilder).collect(Collectors.joining("\n"))) + "\n" +//
                "}";
    }

    private static String getAccessorCode(CNode node) {
        if (node.isArray) {
            return accessorsForArray(node);
        } else if (node.isMap) {
            return accessorsForMap(node);
        } else {
            return accessorForStructOrScalar(node);
        }
    }

    private static String valueAccessor(CNode node) {
        if (node instanceof LeafCNode) {
            return ".value()";
        } else {
            return "";
        }
    }

    private static String listAccessor(CNode node) {
        if (node instanceof LeafCNode) {
            return node.getName() + ".asList()";
        } else {
            return node.getName();
        }
    }

    private static String mapAccessor(CNode node) {
        if (node instanceof LeafCNode) {
            return "LeafNodeMaps.asValueMap(" + node.getName() + ")";
        } else {
            return "Collections.unmodifiableMap(" + node.getName() + ")";
        }
    }

    private static String accessorsForArray(CNode node) {
        final String name = node.getName();
        final String fullName = node.getFullName();
        return "/**\n" +//
                " * @return " + fullName + "\n" +//
                " */\n" +//
                "public List<" + boxedDataType(node) + "> " + name + "() {\n" +//
                "  return " + listAccessor(node) + ";\n" +//
                "}\n" +//
                "\n" +//
                "/**\n" +//
                " * @param i the index of the value to return\n" +//
                " * @return " + fullName + "\n" +//
                " */\n" +//
                "public " + userDataType(node) + " " + name + "(int i) {\n" +//
                "  return " + name + ".get(i)" + valueAccessor(node) + ";\n" +//
                "}";
    }

    private static String accessorsForMap(CNode node) {
        final String name = node.getName();
        final String fullName = node.getFullName();

        return "/**\n" +//
                " * @return " + fullName + "\n" +//
                " */\n" +//
                "public Map<String, " + boxedDataType(node) + "> " + name + "() {\n" +//
                "  return " + mapAccessor(node) + ";\n" +//
                "}\n" +//
                "\n" +//
                "/**\n" +//
                " * @param key the key of the value to return\n" +//
                " * @return " + fullName + "\n" +//
                " */\n" +//
                "public " + userDataType(node) + " " + name + "(String key) {\n" +//
                "  return " + name + ".get(key)" + valueAccessor(node) + ";\n" +//
                "}";
    }

    private static String accessorForStructOrScalar(CNode node) {
        return "/**\n" +//
                " * @return " + node.getFullName() + "\n" +//
                " */\n" +//
                "public " + userDataType(node) + " " + node.getName() + "() {\n" +//
                "  return " + node.getName() + valueAccessor(node) + ";\n" +//
                "}";
    }

    private static String getAccessors(CNode[] children) {
        List<String> accessors = new LinkedList<>();
        for (CNode child : children) {
            String accessor = getAccessorCode(child);
            if (accessor.isEmpty() == false) {
                accessors.add(accessor);
            }
        }
        return String.join("\n\n", accessors);
    }

    private static String getStaticMethodsForInnerArray(InnerCNode inner) {
        final String nc = nodeClass(inner);
        return String.format("private static InnerNodeVector<%s> createVector(List<Builder> builders) {\n" +//
                "    List<%s> elems = new ArrayList<>();\n" +//
                "    for (Builder b : builders) {\n" +//
                "        elems.add(new %s(b));\n" +//
                "    }\n" +//
                "    return new InnerNodeVector<%s>(elems);\n" +//
                "}", nc, nc, nc, nc);
    }

    private static String getStaticMethodsForInnerMap(InnerCNode inner) {
        final String nc = nodeClass(inner);
        return String.format(
                "private static Map<String, %s> createMap(Map<String, Builder> builders) {\n" +//
                        "  Map<String, %s> ret = new LinkedHashMap<>();\n" +//
                        "  for(String key : builders.keySet()) {\n" +//
                        "    ret.put(key, new %s(builders.get(key)));\n" +//
                        "  }\n" +//
                        "  return Collections.unmodifiableMap(ret);\n" +//
                        "}", nc, nc, nc);
    }

    private static String getEnumCode(EnumLeaf en) {
        String enumValues = stream(en.getLegalValues()).map(e -> String.format("  public final static Enum %s = Enum.%s;", e, e)).collect(Collectors.joining("\n"));

        String code = String.format("%s\n" +//
                        "public final static class %s extends EnumNode<%s> {\n" +//
                        "\n" +//
                        "  public %s(){\n" +//
                        "    this.value = null;\n" +//
                        "  }\n" +//
                        "\n" +//
                        "  public %s(Enum enumValue) {\n" +//
                        "    super(enumValue != null);\n" +//
                        "    this.value = enumValue;\n" +//
                        "  }\n" +//
                        "\n" +//
                        "  public enum Enum {%s}\n" +//
                        "%s\n" +//
                        "\n" +//
                        "  @Override\n" +//
                        "  protected boolean doSetValue(@NonNull String name) {\n" +//
                        "    try {\n" +//
                        "      value = Enum.valueOf(name);\n" +//
                        "      return true;\n" +//
                        "    } catch (IllegalArgumentException e) {\n" +//
                        "    }\n" +//
                        "    return false;\n" +//
                        "  }\n" +//
                        "}", getClassDoc(en),
                nodeClass(en),
                nodeClass(en) + ".Enum",
                nodeClass(en),
                nodeClass(en),
                String.join(", ", en.getLegalValues()),
                enumValues);

        return indentCode("", code);
    }

    private static String getClassDoc(CNode node) {
        String header = "/**\n" + " * This class represents " + node.getFullName();
        String nodeComment = node.getCommentBlock(" *");
        if (nodeComment.isEmpty()) {
            return header + "\n */";
        } else {
            if (nodeComment.endsWith("\n")) {
                nodeComment = nodeComment.substring(0, nodeComment.length() - 1);
            }
            return header + "\n * \n" + nodeComment + "\n */";
        }
    }

    static String indentCode(String indent, String code) {
        List<String> indented = new LinkedList<>();
        for (String line : code.split("\n", -1)) {
            indented.add(line.length() > 0 ? indent + line : line);
        }
        return String.join("\n", indented);
    }

    /**
     * @return the name of the class that is generated by this node.
     */
    static String nodeClass(CNode node) {
        if (node.getName().length() == 0) {
            throw new CodegenRuntimeException("Node with empty name, under parent " + node.getParent().getName());
        } else if (node instanceof InnerCNode && node.getParent() == null) {
            return ConfiggenUtil.createClassName(node.getName());
        } else if (node instanceof BooleanLeaf) {
            return "BooleanNode";
        } else if (node instanceof DoubleLeaf) {
            return "DoubleNode";
        } else if (node instanceof FileLeaf) {
            return "FileNode";
        } else if (node instanceof PathLeaf) {
            return "PathNode";
        } else if (node instanceof IntegerLeaf) {
            return "IntegerNode";
        } else if (node instanceof LongLeaf) {
            return "LongNode";
        } else if (node instanceof ReferenceLeaf) {
            return "ReferenceNode";
        } else if (node instanceof StringLeaf) {
            return "StringNode";
        } else {
            return ConfiggenUtil.capitalize(node.getName());
        }
    }

    static String userDataType(CNode node) {
        if (node instanceof InnerCNode) {
            return nodeClass(node);
        } else if (node instanceof EnumLeaf) {
            return nodeClass(node) + ".Enum";
        } else if (node instanceof BooleanLeaf) {
            return "boolean";
        } else if (node instanceof DoubleLeaf) {
            return "double";
        } else if (node instanceof FileLeaf) {
            return "FileReference";
        } else if (node instanceof PathLeaf) {
            return "Path";
        } else if (node instanceof IntegerLeaf) {
            return "int";
        } else if (node instanceof LongLeaf) {
            return "long";
        } else if (node instanceof StringLeaf) {
            return "String";
        } else {
            throw new IllegalStateException("Cannot determine user data type for node"); // should not occur
        }
    }

    /**
     * @return the boxed java data type, e.g. Integer for int
     */
    static String boxedDataType(CNode node) {
        String rawType = userDataType(node);

        if ("int".equals(rawType)) {
            return "Integer";
        } else if (rawType.toLowerCase().equals(rawType)) {
            return ConfiggenUtil.capitalize(rawType);
        } else {
            return rawType;
        }
    }
}
