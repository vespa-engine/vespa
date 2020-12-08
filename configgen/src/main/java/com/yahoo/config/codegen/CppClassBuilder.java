// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.codegen;

import java.io.FileWriter;
import java.io.File;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.FileReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Arrays;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

/**
 * This class autogenerates C++ code for the C++ config, based on a CNode tree given.
 */
public class CppClassBuilder implements ClassBuilder {

    private final CNode root;
    private final NormalizedDefinition nd;
    private final File rootDir;
    private final String relativePathUnderRoot;
    private static final Map<String, String> vectorTypeDefs;
    static {
        Map<String, String> map = new HashMap<String, String>();
        map.put("bool", "BoolVector");
        map.put("int32_t", "IntVector");
        map.put("int64_t", "LongVector");
        map.put("double", "DoubleVector");
        map.put("vespalib::string", "StringVector");
        vectorTypeDefs = Collections.unmodifiableMap(map);
    }
    private static final Map<String, String> mapTypeDefs;
    static {
        Map<String, String> map = new HashMap<>();
        map.put("bool", "BoolMap");
        map.put("int32_t", "IntMap");
        map.put("int64_t", "LongMap");
        map.put("double", "DoubleMap");
        map.put("vespalib::string", "StringMap");
        mapTypeDefs = Collections.unmodifiableMap(map);
    }
    private static final Map<String, String> slimeTypeMap;
    static {
        Map<String, String> map = new HashMap<String, String>();
        map.put("bool", "Bool");
        map.put("int", "Long");
        map.put("long", "Long");
        map.put("double", "Double");
        map.put("string", "String");
        map.put("enum", "String");
        map.put("file", "String");
        map.put("reference", "String");
        slimeTypeMap = Collections.unmodifiableMap(map);
    }

    public CppClassBuilder(CNode root, NormalizedDefinition nd, File rootDir, String relativePathUnderRoot) {
        this.root = root;
        this.nd = nd;
        this.rootDir = rootDir;
        this.relativePathUnderRoot = relativePathUnderRoot;
    }

    public void createConfigClasses() {
        generateConfig(root, nd);
    }

    String readFile(File f) throws IOException {
        if (!f.isFile()) return null;
        StringBuilder sb = new StringBuilder();
        try (BufferedReader sr = new BufferedReader(new FileReader(f))) {
            while (true) {
                String line = sr.readLine();
                if (line == null) break;
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }

    void writeFile(File f, String content) throws IOException {
        FileWriter fw = new FileWriter(f);
        fw.write(content);
        fw.close();
    }

    void generateConfig(CNode root, NormalizedDefinition nd) {
        try{
            StringWriter headerWriter = new StringWriter();
            StringWriter bodyWriter = new StringWriter();
            writeHeaderFile(headerWriter, root);
            writeBodyFile(bodyWriter, root, relativePathUnderRoot, nd);

            String newHeader = headerWriter.toString();
            String newBody = bodyWriter.toString();

            File headerFile = new File(rootDir, relativePathUnderRoot + "/" + getFileName(root, "h"));
            File bodyFile = new File(rootDir, relativePathUnderRoot + "/" + getFileName(root, "cpp"));

            writeFile(headerFile, newHeader);
            writeFile(bodyFile, newBody);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    String getFileName(CNode node, String extension) {
        return "config-" + node.getName() + "." + extension;
    }

    static String removeDashesAndUpperCaseAllFirstChars(String source, boolean capitalizeFirst) {
        // Create upper case chars after each dash
        String parts[] = source.split("[-_]");
        StringBuilder sb = new StringBuilder();
        for (String s : parts) {
            sb.append(s.substring(0, 1).toUpperCase()).append(s.substring(1));
        }
        String result = sb.toString();
        if (!capitalizeFirst) {
            result = result.substring(0,1).toLowerCase() + result.substring(1);
        }
        return result;
    }

    /** Convert name of type to the name we want to use in macro ifdefs in file. */
    String getDefineName(String name) {
        return name.toUpperCase().replace("-", "");
    }

    /** Convert name of type to the name we want to use as type name in the generated code. */
    static String getTypeName(String name) {
        return removeDashesAndUpperCaseAllFirstChars(name, true);
    }

    /** Convert name of an identifier from value in def file to name to use in C++ file. */
    String getIdentifier(String name) {
        return removeDashesAndUpperCaseAllFirstChars(name, false);
    }

    /**
     * Class to generate noexcept specifier if default constructor, copy constructor or copy assignment is non-throwing
     */
    private static class NoExceptSpecifier {
        private boolean enabled;

        public NoExceptSpecifier(CNode node) {
                enabled = checkNode(node);
        }

        private static boolean checkNode(CNode node) {
            if (node instanceof InnerCNode) {
                for (CNode child: node.getChildren()) {
                    if (child.isArray || child.isMap) {
                        return false;
                    }
                    if (!checkNode(child)) {
                        return false;
                    }
                }
            }
            return true;
        }

        public String toString() {
            if (enabled) {
                return " noexcept";
            } else {
                return "";
            }
        }
    }

    void writeHeaderFile(Writer w, CNode root) throws IOException {
        writeHeaderHeader(w, root);
        writeHeaderPublic(w, root);
        writeHeaderFooter(w, root);
    }

    void writeHeaderPublic(Writer w, CNode root) throws IOException {
        w.write("public:\n");
        writeHeaderTypeDefs(w, root, "    ");
        writeTypeDeclarations(w, root, "    ");
        writeHeaderFunctionDeclarations(w, getTypeName(root, false), root, "    ");
        writeStaticMemberDeclarations(w, "    ");
        writeMembers(w, root, "    ");
    }

    String [] generateCppNameSpace(CNode root) {
        String namespace = root.getNamespace();
        if (namespace.contains(".")) {
            return namespace.split("\\.");
        }
        return new String[]{namespace};
    }

    String generateCppNameSpaceString(String[] namespaceList) {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < namespaceList.length - 1; i++) {
            str.append(namespaceList[i]);
            str.append("::");
        }
        str.append(namespaceList[namespaceList.length - 1]);
        return str.toString();
    }

    String generateCppNameSpaceDefine(String[] namespaceList) {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < namespaceList.length - 1; i++) {
            str.append(namespaceList[i].toUpperCase());
            str.append("_");
        }
        str.append(namespaceList[namespaceList.length - 1].toUpperCase());
        return str.toString();
    }

    void writeNameSpaceBegin(Writer w, String [] namespaceList) throws IOException {
        w.write("namespace ");
        w.write(getNestedNameSpace(namespaceList));
        w.write(" {\n");
    }

    String getNestedNameSpace(String [] namespaceList) {
        return Arrays.stream(namespaceList).map(String::toString).collect(Collectors.joining("::"));
    }

    void writeNameSpaceEnd(Writer w, String [] namespaceList) throws IOException {
        w.write("} // namespace ");
        w.write(getNestedNameSpace(namespaceList));
        w.write("\n");
    }

    void writeHeaderHeader(Writer w, CNode root) throws IOException {
        String [] namespaceList = generateCppNameSpace(root);
        String namespacePrint = generateCppNameSpaceString(namespaceList);
        String namespaceDefine = generateCppNameSpaceDefine(namespaceList);
        String className = getTypeName(root, false);
        String defineName = namespaceDefine + "_" + getDefineName(className);
        w.write(""
                + "/**\n"
                + " * @class " + namespacePrint + "::" + className + "\n"
                + " * @ingroup config\n"
                + " *\n"
                + " * @brief This is an autogenerated class for handling VESPA config.\n"
                + " *\n"
                + " * This class is autogenerated by vespa from a config definition file.\n"
                + " * To subscribe to config, you need to include the config/config.h header, \n"
                + " * and create a ConfigSubscriber in order to subscribe for config.\n"
                );
        if (root.getComment().length() > 0) {
            w.write(" *\n");
            StringTokenizer st = new StringTokenizer(root.getComment(), "\n");
            while (st.hasMoreTokens()) {
                w.write(" * " + st.nextToken() + "\n");
            }
        }
        w.write(""
                + " */\n"
                + "#ifndef CLOUD_CONFIG_" + defineName + "_H\n"
                + "#define CLOUD_CONFIG_" + defineName + "_H\n"
                + "\n"
                + "#include <vespa/config/configgen/configinstance.h>\n"
                + "#include <vespa/vespalib/stllike/string.h>\n"
                + "#include <vector>\n"
                + "#include <map>\n"
                + "\n");
        w.write("namespace config {\n");
        w.write("    class ConfigValue;\n");
        w.write("    class ConfigPayload;\n");
        w.write("}\n\n");
        w.write("namespace vespalib::slime {\n");
        w.write("    struct Inspector;\n");
        w.write("    struct Cursor;\n");
        w.write("}\n\n");
        writeNameSpaceBegin(w, namespaceList);
        w.write("\nnamespace internal {\n\n");
        w.write(""
                + "/**\n"
                + " * This class contains the config. DO NOT USE THIS CLASS DIRECTLY. Use the typedeffed\n"
                + " * versions after this class declaration.\n"
                + " */\n"
                + "class Internal" + className + "Type : public ::config::ConfigInstance\n"
                + "{\n"
                );

    }



    void writeTypeDeclarations(Writer w, CNode node, String indent) throws IOException {
        java.util.Set<String> declaredTypes = new java.util.HashSet<String>();
        for (CNode child : node.getChildren()) {
            boolean complexType = (child instanceof InnerCNode || child instanceof LeafCNode.EnumLeaf);
            if (complexType && !declaredTypes.contains(child.getName())) {
                String typeName = getTypeName(child, false);
                declaredTypes.add(child.getName());
                if (child instanceof LeafCNode.EnumLeaf) {
                    w.write(indent + "enum class " + typeName + " { ");
                    LeafCNode.EnumLeaf leaf = (LeafCNode.EnumLeaf) child;
                    for (int i=0; i<leaf.getLegalValues().length; ++i) {
                        if (i != 0) {
                            w.write(", ");
                        }
                        w.write(leaf.getLegalValues()[i]);
                    }
                    w.write(" };\n"
                            + indent + "typedef std::vector<" + typeName + "> "
                            + typeName + "Vector;"
                            + "\n"
                            + indent + "typedef std::map<vespalib::string, " + typeName + "> "
                            + typeName + "Map;"
                            + "\n"
                            + indent + "static " + typeName + " get" + typeName + "(const vespalib::string&);\n"
                            + indent + "static vespalib::string get" + typeName + "Name(" + typeName + " e);\n"
                            + "\n"
                            );
                    w.write(indent + "struct Internal" + typeName + "Converter {\n");
                    w.write(indent + "    " + typeName + " operator()(const ::vespalib::string & __fieldName, const ::vespalib::slime::Inspector & __inspector);\n");
                    w.write(indent + "    " + typeName + " operator()(const ::vespalib::slime::Inspector & __inspector);\n");
                    w.write(indent + "    " + typeName + " operator()(const ::vespalib::slime::Inspector & __inspector, " + typeName + " __eDefault);\n");
                    w.write(indent + "};\n");
                } else {
                    w.write(indent + "class " + typeName + " {\n");
                    w.write(indent + "public:\n");
                    writeTypeDeclarations(w, child, indent + "    ");
                    writeStructFunctionDeclarations(w, getTypeName(child, false), child, indent + "    ");
                    writeMembers(w, child, indent + "    ");
                    w.write(indent + "};\n");
                    w.write(indent + "typedef std::vector<" + typeName + "> " + typeName + "Vector;\n\n");
                    w.write(indent + "typedef std::map<vespalib::string, " + typeName + "> " + typeName + "Map;\n\n");
                }
            }
        }
    }

    void writeHeaderFunctionDeclarations(Writer w, String className, CNode node, String indent) throws IOException {
        w.write(""
                + indent + "const vespalib::string & defName() const override { return CONFIG_DEF_NAME; }\n"
                + indent + "const vespalib::string & defMd5() const override { return CONFIG_DEF_MD5; }\n"
                + indent + "const vespalib::string & defNamespace() const override { return CONFIG_DEF_NAMESPACE; }\n"
                + indent + "void serialize(::config::ConfigDataBuffer & __buffer) const override;\n");
        writeConfigClassFunctionDeclarations(w, "Internal" + className + "Type", node, indent);
    }

    void writeConfigClassFunctionDeclarations(Writer w, String className, CNode node, String indent) throws IOException {
        w.write(indent + className + "(const ::config::ConfigValue & __value);\n");
        w.write(indent + className + "(const ::config::ConfigDataBuffer & __value);\n");
        w.write(indent + className + "(const ::config::ConfigPayload & __payload);\n");
        writeCommonFunctionDeclarations(w, className, node, indent);
    }

    void writeStructFunctionDeclarations(Writer w, String className, CNode node, String indent) throws IOException {
        w.write(indent + className + "(const std::vector<vespalib::string> & __lines);\n");
        w.write(indent + className + "(const vespalib::slime::Inspector & __inspector);\n");
        w.write(indent + className + "(const ::config::ConfigPayload & __payload);\n");
        writeCommonFunctionDeclarations(w, className, node, indent);
        w.write(indent + "void serialize(vespalib::slime::Cursor & __cursor) const;\n");
    }

    void writeClassCopyConstructorDeclaration(Writer w, String className, NoExceptSpecifier noexcept, String indent) throws IOException {
        w.write(indent + className + "(const " + className + " & __rhs)" + noexcept + ";\n");
    }
    void writeClassAssignmentOperatorDeclaration(Writer w, String className, NoExceptSpecifier noexcept, String indent) throws IOException {
        w.write(indent + className + " & operator = (const " + className + " & __rhs)" + noexcept + ";\n");
    }

    void writeConfigClassCopyConstructorDefinition(Writer w, String parent, String className, NoExceptSpecifier noexcept) throws IOException {
        w.write(parent + "::" + className + "(const " + className + " & __rhs)" + noexcept + " = default;\n");
    }
    void writeConfigClassAssignmentOperatorDefinition(Writer w, String parent, String className, NoExceptSpecifier noexcept) throws IOException {
        w.write(parent + " & " + parent + "::" + "operator =(const " + className + " & __rhs)" + noexcept + " = default;\n");
    }

    void writeClassCopyConstructorDefinition(Writer w, String parent, CNode node) throws IOException {
        String typeName = getTypeName(node, false);
        NoExceptSpecifier noexcept = new NoExceptSpecifier(node);
        w.write(parent + "::" + typeName + "(const " + typeName + " & __rhs)" + noexcept + " = default;\n");
    }

    void writeClassAssignmentOperatorDefinition(Writer w, String parent, CNode node) throws IOException {
        String typeName = getTypeName(node, false);
        NoExceptSpecifier noexcept = new NoExceptSpecifier(node);
        // Write empty constructor
        w.write(parent + " & " + parent + "::" + "operator = (const " + typeName + " & __rhs)" + noexcept + " = default;\n");
    }

    void writeDestructor(Writer w, String parent, String className) throws IOException {
        w.write(parent + "~" + className + "() { } \n");
    }

    void writeCommonFunctionDeclarations(Writer w, String className, CNode node, String indent) throws IOException {
        NoExceptSpecifier noexcept = new NoExceptSpecifier(node);
        w.write("" + indent + className + "() " + noexcept + ";\n");
        writeClassCopyConstructorDeclaration(w, className, noexcept, indent);
        writeClassAssignmentOperatorDeclaration(w, className, noexcept, indent);
        w.write("" + indent + "~" + className + "();\n");
        w.write("\n"
                + indent + "bool operator==(const " + className + "& __rhs) const noexcept;\n"
                + indent + "bool operator!=(const " + className + "& __rhs) const noexcept;\n"
                + "\n"
                );
    }

    static String getTypeName(CNode node, boolean includeArray) {
        String type = null;
        if (node instanceof InnerCNode) {
            InnerCNode innerNode = (InnerCNode) node;
            type = getTypeName(innerNode.getName());
        } else if (node instanceof LeafCNode) {
            LeafCNode leaf = (LeafCNode) node;
            if (leaf.getType().equals("bool")) {
                type = "bool";
            } else if (leaf.getType().equals("int")) {
                type = "int32_t";
            } else if (leaf.getType().equals("long")) {
                type = "int64_t";
            } else if (leaf.getType().equals("double")) {
                type = "double";
            } else if (leaf.getType().equals("enum")) {
                type = getTypeName(node.getName());
            } else if (leaf.getType().equals("string")) {
                type = "vespalib::string";
            } else if (leaf.getType().equals("reference")) {
                type = "vespalib::string";
            } else if (leaf.getType().equals("file")) {
                type = "vespalib::string";
            } else {
                throw new IllegalArgumentException("Unknown leaf datatype " + leaf.getType());
            }
        }
        if (type == null) {
            throw new IllegalArgumentException("Unknown node " + node);
        }
        if (node.isArray && includeArray) {
            if (vectorTypeDefs.containsKey(type)) {
                type = vectorTypeDefs.get(type);
            } else {
                type = type + "Vector";
            }
        } else if (node.isMap && includeArray) {
            if (mapTypeDefs.containsKey(type)) {
                type = mapTypeDefs.get(type);
            } else {
                type = type + "Map";
            }
        }
        return type;
    }

    void writeStaticMemberDeclarations(Writer w, String indent) throws IOException {
        w.write(""
                + indent + "static const vespalib::string CONFIG_DEF_MD5;\n"
                + indent + "static const vespalib::string CONFIG_DEF_VERSION;\n"
                + indent + "static const vespalib::string CONFIG_DEF_NAME;\n"
                + indent + "static const vespalib::string CONFIG_DEF_NAMESPACE;\n"
                + indent + "static const std::vector<vespalib::string> CONFIG_DEF_SCHEMA;\n"
                + indent + "static const int64_t CONFIG_DEF_SERIALIZE_VERSION;\n"
                + "\n"
                );
    }

    void writeComment(Writer w, String indent, String comment, boolean javadoc)
        throws IOException
    {
        /** If simple one liner comment, write on one line. */
        if (javadoc && comment.indexOf('\n') == -1
            && comment.length() <= 80 - (indent.length() + 7))
        {
            w.write(indent + "/** " + comment + " */\n");
            return;
        } else if (!javadoc && comment.indexOf('\n') == -1
                   && comment.length() <= 80 - (indent.length() + 3))
        {
            w.write(indent + "// " + comment + "\n");
            return;
        }
        /** If not we need to write multi line comment. */
        int maxLineLen = 80 - (indent.length() + 3);
        if (javadoc) w.write(indent + "/**\n");
        do {
            String current;
            // Extract first line to write
            int newLine = comment.indexOf('\n');
            if (newLine == -1) {
                current = comment;
                comment = "";
            } else {
                current = comment.substring(0, newLine);
                comment = comment.substring(newLine + 1);
            }
            // If line too long, cut it in two
            if (current.length() > maxLineLen) {
                int spaceIndex = current.lastIndexOf(' ', maxLineLen);
                if (spaceIndex >= maxLineLen - 15) {
                    comment = current.substring(spaceIndex + 1)
                              + "\n" + comment;
                    current = current.substring(0, spaceIndex);
                } else {
                    comment = current.substring(maxLineLen) + "\n" + comment;
                    current = current.substring(0, maxLineLen) + "-";
                }
            }
            w.write(indent + (javadoc ? " * " : "// ") + current + "\n");
        } while (comment.length() > 0);
        if (javadoc) w.write(indent + " */\n");
    }

    void writeMembers(Writer w, CNode node, String indent) throws IOException {
        for (CNode child : node.getChildren()) {
            String typeName = getTypeName(child, true);
            if (child.getComment().length() > 0) {
                String comment = child.getComment();
                int index;
                do {
                    index = comment.indexOf("\n\n");
                    if (index == -1) break;
                    String next = comment.substring(0, index);
                    comment = comment.substring(index + 2);
                    w.write("\n");
                    writeComment(w, indent, next, false);
                } while (true);
                w.write("\n");
                writeComment(w, indent, comment, true);
            }
            w.write(indent + typeName + " " + getIdentifier(child.getName()) + ";");
            if (child instanceof LeafCNode) {
                LeafCNode leaf = (LeafCNode) child;
                DefaultValue value = leaf.getDefaultValue();
                if (value != null) {
                    w.write(" // Default: " + value.getStringRepresentation());
                }
            }
            w.write("\n");
        }
    }

    void writeHeaderTypeDefs(Writer w, CNode root, String indent) throws IOException {
        w.write(indent + "typedef std::unique_ptr<const " + getInternalClassName(root) + "> UP;\n");
        for (Map.Entry<String, String> entry : vectorTypeDefs.entrySet()) {
            String typeName = entry.getKey();
            String vectorName = entry.getValue();
            String typeDef = "typedef std::vector<" + typeName + "> " + vectorName;
            w.write(indent + typeDef + ";\n");
        }
        for (Map.Entry<String, String> entry : mapTypeDefs.entrySet()) {
            String typeName = entry.getKey();
            String mapName = entry.getValue();
            String typeDef = "typedef std::map<vespalib::string, " + typeName + "> " + mapName;
            w.write(indent + typeDef + ";\n");
        }
    }

    private static String getInternalClassName(CNode root) {
        return "Internal" + getTypeName(root, false) + "Type";
    }

    void writeHeaderFooter(Writer w, CNode root) throws IOException {
        String [] namespaceList = generateCppNameSpace(root);
        String namespaceDefine = generateCppNameSpaceDefine(namespaceList);

        String className = getTypeName(root, false);
        String defineName = namespaceDefine + "_" + getDefineName(className);

        w.write(""
                + "};\n"
                + "\n"
                + "} // namespace internal\n\n");

        w.write("typedef internal::" + getInternalClassName(root) + " " + className + "ConfigBuilder;\n");
        w.write("typedef const internal::" + getInternalClassName(root) + " " + className + "Config;\n");
        w.write("\n");
        writeNameSpaceEnd(w, namespaceList);
        w.write("#endif // VESPA_config_" + defineName + "_H\n");
    }

    void writeBodyFile(Writer w, CNode root, String subdir, NormalizedDefinition nd) throws IOException {
        writeBodyHeader(w, root, subdir);
        writeStaticMemberDefinitions(w, root, nd);
        writeDefinition(w, root, null);
        writeBodyFooter(w, root);
    }

    void writeBodyHeader(Writer w, CNode root, String subdir) throws IOException {
        if (subdir == null) {
            w.write("#include \"" + getFileName(root, "h") + "\"");
        } else {
            w.write("#include <" + subdir + "/" + getFileName(root, "h") + ">");
        }
        w.write("\n");
        w.write("#include <vespa/config/common/configvalue.h>\n");
        w.write("#include <vespa/config/common/exceptions.h>\n");
        w.write("#include <vespa/config/configgen/configpayload.h>\n");
        w.write("#include <vespa/config/print/configdatabuffer.h>\n");
        w.write("#include <vespa/config/common/configparser.h>\n");
        w.write("#include <vespa/config/configgen/vector_inserter.h>\n");
        w.write("#include <vespa/config/configgen/map_inserter.h>\n");
        w.write("#include <vespa/vespalib/data/slime/convenience.h>\n");
        w.write("#include <vespa/vespalib/data/slime/slime.h>\n");
        w.write("#include <vespa/vespalib/stllike/asciistream.h>\n");
        w.write("\n");
        writeNameSpaceBegin(w, generateCppNameSpace(root));
        w.write("\nnamespace internal {\n\n");
        w.write("using ::config::ConfigParser;\n");
        w.write("using ::config::InvalidConfigException;\n");
        w.write("using ::config::ConfigInstance;\n");
        w.write("using ::config::ConfigValue;\n");
        w.write("using namespace vespalib::slime::convenience;\n");
        w.write("\n");
    }

    void writeStaticMemberDefinitions(Writer w, CNode root, NormalizedDefinition nd) throws IOException {
        String typeName = getInternalClassName(root);
        w.write("const vespalib::string " + typeName + "::CONFIG_DEF_MD5(\"" + root.defMd5 + "\");\n"
                + "const vespalib::string " + typeName + "::CONFIG_DEF_VERSION(\"" + root.defVersion + "\");\n"
                + "const vespalib::string " + typeName + "::CONFIG_DEF_NAME(\"" + root.defName + "\");\n"
                + "const vespalib::string " + typeName + "::CONFIG_DEF_NAMESPACE(\"" + root.getNamespace() + "\");\n"
                + "const int64_t " + typeName + "::CONFIG_DEF_SERIALIZE_VERSION(1);\n");
        w.write("const static vespalib::string __internalDefSchema[] = {\n");
        for (String line : nd.getNormalizedContent()) {
            w.write("\"" + line.replace("\"", "\\\"") + "\",\n");
        }
        w.write("};\n");
        w.write("const std::vector<vespalib::string> " + typeName + "::CONFIG_DEF_SCHEMA(__internalDefSchema,\n");
        w.write("    __internalDefSchema + (sizeof(__internalDefSchema) / \n");
        w.write("                           sizeof(__internalDefSchema[0])));\n");
        w.write("\n");
    }

    void writeDefinition(Writer w, CNode node, String fullClassName) throws IOException {
        boolean root = false;
        if (fullClassName == null) {
            fullClassName =  getInternalClassName(node);
            root = true;
        }
        final String parent = fullClassName + "::";
        java.util.Set<String> declaredTypes = new java.util.HashSet<String>();
        for (CNode child : node.getChildren()) {
            boolean complexType = (child instanceof InnerCNode || child instanceof LeafCNode.EnumLeaf);
            if (complexType && !declaredTypes.contains(child.getName())) {
                String typeName = getTypeName(child, false);
                declaredTypes.add(child.getName());
                if (child instanceof LeafCNode.EnumLeaf) {
                    LeafCNode.EnumLeaf leaf = (LeafCNode.EnumLeaf) child;
                    // Definition of getType(string)
                    w.write(parent + typeName + "\n"
                            + parent + "get" + typeName + "(const vespalib::string& name)\n"
                            + "{\n"
                            );
                    for (int i=0; i<leaf.getLegalValues().length; ++i) {
                        w.write("    " + (i != 0 ? "} else " : ""));
                        w.write("if (name == \"" + leaf.getLegalValues()[i] + "\") {\n"
                                + "        return " + typeName + "::" + leaf.getLegalValues()[i] + ";\n");
                    }
                    w.write("    } else {\n"
                            + "        throw InvalidConfigException(\"Illegal enum value '\" + name + \"'\");\n"
                            + "    }\n"
                            + "}\n"
                            + "\n"
                            );
                    // Definition of getTypeName(enum)
                    w.write("vespalib::string\n"
                            + parent + "get" + typeName + "Name(" + typeName + " t)\n"
                            + "{\n"
                            + "    switch (t) {\n"
                            );
                    for (int i=0; i<leaf.getLegalValues().length; ++i) {
                        w.write("        case " + typeName + "::" + leaf.getLegalValues()[i]  + ": return \"" + leaf.getLegalValues()[i] + "\";\n");
                    }
                    w.write("        default:\n"
                            + "        {\n"
                            + "            vespalib::asciistream ost;\n"
                            + "            ost << \"UNKNOWN(\" << static_cast<int>(t) << \")\";\n"
                            + "            return ost.str();\n"
                            + "        }\n"
                            + "    }\n"
                            + "}\n"
                            + "\n"
                            );
                    w.write(parent + typeName + " " + parent + "Internal" + typeName + "Converter::operator()(const ::vespalib::string & __fieldName, const ::vespalib::slime::Inspector & __inspector) {\n");
                    w.write("    if (__inspector.valid()) {\n");
                    w.write("        return " + parent + "get" + typeName + "(__inspector.asString().make_string());\n");
                    w.write("    }\n");
                    w.write("    throw InvalidConfigException(\"Value for '\" + __fieldName + \"' required but not found\");\n");
                    w.write("}\n");
                    w.write(parent + typeName + " " + parent + "Internal" + typeName + "Converter::operator()(const ::vespalib::slime::Inspector & __inspector) {\n");
                    w.write("    return " + parent + "get" + typeName + "(__inspector.asString().make_string());\n");
                    w.write("}\n");
                    w.write(parent + typeName + " " + parent + "Internal" + typeName + "Converter::operator()(const ::vespalib::slime::Inspector & __inspector, " + typeName + " __eDefault) {\n");
                    w.write("    if (__inspector.valid()) {\n");
                    w.write("        return " + parent + "get" + typeName + "(__inspector.asString().make_string());\n");
                    w.write("    }\n");
                    w.write("    return __eDefault;\n");
                    w.write("}\n\n");
                } else {
                    writeDefinition(w, child, parent + typeName);
                }
            }
        }
        String tmpName = getTypeName(node, false);
        String typeName = root ? getInternalClassName(node) : tmpName;
        NoExceptSpecifier noexcept = new NoExceptSpecifier(node);
        // Write empty constructor
        w.write(parent + typeName + "()" + noexcept + "\n");
        for (int i=0; i<node.getChildren().length; ++i) {
            CNode child = node.getChildren()[i];
            String childName = getIdentifier(child.getName());
            if (i == 0) {
                w.write("    : " + childName + "(");
            } else {
                w.write("),\n      " + childName + "(");
            }
            if (child.isArray || child.isMap) {
                // Default array for empty constructor is empty array.
            } else if (child instanceof LeafCNode) { // If we have a default value, use that..
                LeafCNode leaf = (LeafCNode) child;
                if (leaf.getDefaultValue() != null) {
                    if (leaf.getType().equals("enum")) {
                        w.write(getTypeName(leaf, false) + "::");
                    }
                    w.write(getDefaultValue(leaf));
                } else {
                    // Defines empty constructor defaults for primitives without default set
                    if (leaf.getType().equals("bool")) {
                        w.write("false");
                    } else if (leaf.getType().equals("int")) {
                        w.write("0");
                    } else if (leaf.getType().equals("double")) {
                        w.write("0");
                    } else if (leaf.getType().equals("string")) {
                    } else if (leaf.getType().equals("enum")) {
                        LeafCNode.EnumLeaf enumNode = (LeafCNode.EnumLeaf) leaf;
                        w.write(getTypeName(leaf, false) + "::" + enumNode.getLegalValues()[0]);
                    } else if (leaf.getType().equals("reference")) {
                    } else if (leaf.getType().equals("file")) {
                    }
                }
            }
            // If we hit neither else, we're an inner node, thus special type that has its own empty constructor
        }
        if (node.getChildren().length > 0)
            w.write(")\n");
        w.write(""
                + "{\n"
                + "}\n"
                + "\n"
                );
        // Write copy constructor
        if (root) {
            writeConfigClassCopyConstructorDefinition(w, fullClassName, typeName, noexcept);
            writeConfigClassAssignmentOperatorDefinition(w, fullClassName, typeName, noexcept);
        } else {
            writeClassCopyConstructorDefinition(w, fullClassName, node);
            writeClassAssignmentOperatorDefinition(w, fullClassName, node);
        }
        writeDestructor(w, parent, typeName);

        // Write parsing constructor
        String indent = "    ";
        if (root) {
            w.write(typeName + "::" + typeName + "(const ConfigValue & __value)\n"
                    + "{\n"
                    + indent + "try {\n");
            indent = "        ";
            w.write(indent + "const std::vector<vespalib::string> & __lines(__value.getLines());\n");
        } else {
            w.write(parent + typeName + "(const std::vector<vespalib::string> & __lines)\n"
                    + "{\n");
        }
        w.write(""
                + indent + "std::set<vespalib::string> __remainingValuesToParse("
                + "__lines.begin(), __lines.end());\n");
        w.write(indent + "for(std::set<vespalib::string>::iterator __rVTPiter = __remainingValuesToParse.begin();\n"
                + indent + "    __rVTPiter != __remainingValuesToParse.end();)\n"
                + indent + "{\n"
                + indent + "    if (ConfigParser::stripWhitespace(*__rVTPiter).empty()) {\n"
                + indent + "        std::set<vespalib::string>::iterator __rVTPiter2 = __rVTPiter++;\n"
                + indent + "        __remainingValuesToParse.erase(__rVTPiter2);\n"
                + indent + "    } else {\n"
                + indent + "        ++__rVTPiter;\n"
                + indent + "    }\n"
                + indent + "}\n");
        for (CNode child : node.getChildren()) {
            String childType = getTypeName(child, false);
            String childName = getIdentifier(child.getName());
            if (child instanceof LeafCNode.EnumLeaf) {
                if (child.isArray) {
                    w.write(indent + "std::vector<vespalib::string> " + childName + "__ValueList(\n            ");
                } else if (child.isMap) {
                    w.write(indent + "std::map<vespalib::string, vespalib::string> " + childName + "__ValueMap(\n            ");
                } else {
                    w.write(indent + childName + " = get" + childType + "(");
                }
                childType = "vespalib::string";
            } else {
                w.write(indent + childName + " = ");
            }
            if (child.isArray) {
                w.write("ConfigParser::parseArray<" + childType + ">(\""
                        + child.getName() + "\", __lines)");
            } else if (child.isMap) {
                w.write("ConfigParser::parseMap<" + childType + ">(\""
                        + child.getName() + "\", __lines)");
            } else {
                if (child instanceof LeafCNode) {
                    w.write("ConfigParser::parse<" + childType + ">(\""
                            + child.getName() + "\", __lines");
                } else {
                    w.write("ConfigParser::parseStruct<" + childType + ">(\""
                            + child.getName() + "\", __lines");
                }
                if (child instanceof LeafCNode && ((LeafCNode) child).getDefaultValue() != null) {
                    LeafCNode leaf = (LeafCNode) child;
                    if (leaf.getDefaultValue().getValue() != null) {
                        String defaultVal = getDefaultValue(leaf);
                        if (leaf instanceof LeafCNode.EnumLeaf) {
                            defaultVal = '"' + defaultVal + '"';
                        }
                        w.write(", " + defaultVal);
                    }
                }
                w.write(")");
            }
            if (child instanceof LeafCNode.EnumLeaf) {
                childType = getTypeName(child, false);
                w.write(");\n");
                if (child.isArray) {
                    w.write(indent + childName + ".reserve(" + childName + "__ValueList.size());\n"
                            + indent + "for (std::vector<vespalib::string>::const_iterator __it\n"
                            + indent + "        = " + childName + "__ValueList.begin();\n"
                            + indent + "     __it != " + childName + "__ValueList.end(); ++__it)\n"
                            + indent + "{\n"
                            + indent + "    " + childName + ".push_back(get" + childType + "(*__it));\n"
                            + indent + "}\n"
                            );
                } else if (child.isMap) {
                    w.write(indent + "typedef std::map<vespalib::string, vespalib::string> __ValueMap;\n");
                    w.write(indent + "for (__ValueMap::iterator __it(" + childName + "__ValueMap.begin()), __mt(" + childName + "__ValueMap.end()); __it != __mt; __it++) {\n"
                                   + "    " + childName + "[__it->first] = get" + childType + "(__it->second);\n"
                                   + "}\n"
                    );
                }
            } else {
                w.write(";\n");
            }
            w.write(indent + "ConfigParser::stripLinesForKey(\""
                    + child.getName() + "\", "
                    + "__remainingValuesToParse);\n");
        }
        if (root) {
            indent = "    ";
            w.write(indent + "} catch (InvalidConfigException & __ice) {\n");
            w.write(indent + "    throw InvalidConfigException(\"Error parsing config '\" + CONFIG_DEF_NAME + \"' in namespace '\" + CONFIG_DEF_NAMESPACE + \"'"
                    + ": \" + __ice.getMessage());\n"
                    + indent + "}\n");
        }
        w.write("}\n"
                + "\n"
                );
        // Write operator==
        String lineBreak = (parent.length() + typeName.length() < 50 ? "" : "\n");
        w.write("bool\n"
                + parent + lineBreak + "operator==(const " + typeName + "& __rhs) const noexcept\n"
                + "{\n"
                + "    return ("
                );
        for (int i = 0; i<node.getChildren().length; ++i) {
            CNode child = node.getChildren()[i];
            String childName = getIdentifier(child.getName());
            if (i != 0) {
                w.write(" &&\n            ");
            }
            w.write(childName + " == __rhs." + childName);
        }
        w.write(");\n"
                + "}\n"
                + "\n"
                );
        // Write operator!=
        lineBreak = (parent.length() + typeName.length() < 50 ? "" : "\n");
        w.write("bool\n"
                + parent + lineBreak + "operator!=(const " + typeName + "& __rhs) const noexcept\n"
                + "{\n"
                + "    return !(operator==(__rhs));\n"
                + "}\n"
                + "\n"
                );
        writeSlimeEncoder(w, node, parent, root);
        writeSlimeDecoder(w, node, parent, root);
        writeSlimeConstructor(w, node, parent, root);
    }

    public void writeSlimeEncoder(Writer w, CNode node, String parent, boolean root) throws IOException
    {
        String indent = "    ";
        if (root) {
            w.write("void\n"
                    + parent + "serialize(::config::ConfigDataBuffer & __buffer) const\n"
                    + "{\n");
            w.write(indent + "vespalib::Slime & __slime(__buffer.slimeObject());\n");
            w.write(indent + "vespalib::slime::Cursor & __croot = __slime.setObject();\n");
            w.write(indent + "__croot.setDouble(\"version\", CONFIG_DEF_SERIALIZE_VERSION);\n");
            w.write(indent + "vespalib::slime::Cursor & __key = __croot.setObject(\"configKey\");\n");
            w.write(indent + "__key.setString(\"defName\", vespalib::Memory(CONFIG_DEF_NAME));\n");
            w.write(indent + "__key.setString(\"defNamespace\", vespalib::Memory(CONFIG_DEF_NAMESPACE));\n");
            w.write(indent + "__key.setString(\"defMd5\", vespalib::Memory(CONFIG_DEF_MD5));\n");
            w.write(indent + "vespalib::slime::Cursor & __keySchema =__key.setArray(\"defSchema\");\n");
            w.write(indent + "for (size_t i = 0; i < CONFIG_DEF_SCHEMA.size(); i++) {\n");
            w.write(indent + "    __keySchema.addString(vespalib::Memory(CONFIG_DEF_SCHEMA[i]));\n");
            w.write(indent + "}\n");
            w.write(indent + "vespalib::slime::Cursor & __cursor = __croot.setObject(\"configPayload\");\n");
        } else {
            w.write("void\n"
                    + parent + "serialize(vespalib::slime::Cursor & __cursor) const\n"
                    + "{\n");
        }
        for (CNode child : node.getChildren()) {
            String childName = getIdentifier(child.getName());
            String childType = getTypeName(child, false);
            w.write(indent + "{\n");
            indent = "        ";
            w.write(indent + "vespalib::slime::Cursor & __c = __cursor.setObject(\"" + child.getName() + "\");\n");
            if (child.isArray) {
                w.write(indent + "__c.setString(\"type\", \"array\");\n");
                w.write(indent + "vespalib::slime::Cursor & __c2 = __c.setArray(\"value\");\n");
                w.write(indent + "for (size_t __i = 0; __i < " + childName + ".size(); __i++) {\n");
                w.write(indent + "    vespalib::slime::Cursor & __c3 = __c2.addObject();\n");
                if (child instanceof LeafCNode.EnumLeaf) {
                    String repType = slimeTypeMap.get("enum");
                    w.write(indent + "    __c3.setString(\"type\", \"enum\");\n");
                    w.write(indent + "    __c3.set" + repType);
                    w.write("(\"value\", vespalib::Memory(get" + childType + "Name(" + childName + "[__i])));\n");
                } else if (child instanceof LeafCNode) {
                    String type = ((LeafCNode) child).getType();
                    String repType = slimeTypeMap.get(type);
                    w.write(indent + "    __c3.setString(\"type\", \"" + type + "\");\n");
                    w.write(indent + "    __c3.set" + repType);
                    if ("String".equals(repType)) {
                        w.write("(\"value\", vespalib::Memory(" + childName + "[__i]));\n");
                    } else {
                        w.write("(\"value\", " + childName + "[__i]);\n");
                    }
                } else {
                    w.write(indent + "    __c3.setString(\"type\", \"struct\");\n");
                    w.write(indent + "    Cursor & __c4 = __c3.setObject(\"value\");\n");
                    w.write(indent + "    " + childName + "[__i].serialize(__c4);\n");
                }
                w.write(indent + "}\n");
            } else if (child.isMap) {
                w.write(indent + "__c.setString(\"type\", \"map\");\n");
                w.write(indent + "vespalib::slime::Cursor & __c2 = __c.setArray(\"value\");\n");
                String childMapType = getTypeName(child, true);
                w.write(indent + "for (" + childMapType + "::const_iterator it(" + childName + ".begin()), mt(" + childName + ".end()); it != mt; it++) {\n");
                w.write(indent + "    vespalib::slime::Cursor & __c3 = __c2.addObject();\n");
                w.write(indent + "    __c3.setString(\"key\", vespalib::Memory(it->first));\n");
                if (child instanceof LeafCNode.EnumLeaf) {
                    String repType = slimeTypeMap.get("enum");
                    w.write(indent + "    __c3.setString(\"type\", \"enum\");\n");
                    w.write(indent + "    __c3.set" + repType);
                    w.write("(\"value\", vespalib::Memory(get" + childType + "Name(it->second)));\n");
                } else if (child instanceof LeafCNode) {
                    String type = ((LeafCNode) child).getType();
                    String repType = slimeTypeMap.get(type);
                    w.write(indent + "    __c3.setString(\"type\", \"" + type + "\");\n");
                    w.write(indent + "    __c3.set" + repType);
                    if ("String".equals(repType)) {
                        w.write("(\"value\", vespalib::Memory(it->second));\n");
                    } else {
                        w.write("(\"value\", it->second);\n");
                    }
                } else {
                    w.write(indent + "    __c3.setString(\"type\", \"struct\");\n");
                    w.write(indent + "    Cursor & __c4 = __c3.setObject(\"value\");\n");
                    w.write(indent + "    it->second.serialize(__c4);\n");
                }
                w.write(indent + "}\n");
            } else {
                if (child instanceof LeafCNode.EnumLeaf) {
                    String repType = slimeTypeMap.get("enum");
                    w.write(indent + "__c.setString(\"type\", \"enum\");\n");
                    w.write(indent + "__c.set" + repType);
                    w.write("(\"value\", vespalib::Memory(get" + childType + "Name(" + childName + ")));\n");
                } else if (child instanceof LeafCNode) {
                    String type = ((LeafCNode) child).getType();
                    String repType = slimeTypeMap.get(type);
                    w.write(indent + "__c.setString(\"type\", \"" + type + "\");\n");
                    w.write(indent + "__c.set" + repType);
                    if ("String".equals(repType)) {
                        w.write("(\"value\", vespalib::Memory(" + childName + "));\n");
                    } else {
                        w.write("(\"value\", " + childName + ");\n");
                    }
                } else {
                    w.write(indent + "__c.setString(\"type\", \"struct\");\n");
                    w.write(indent + "Cursor & __c2 = __c.setObject(\"value\");\n");
                    w.write(indent + childName + ".serialize(__c2);\n");
                }
            }
            indent = "    ";
            w.write(indent + "}\n");

        }
        w.write("}\n\n");
    }

    public void writeSlimeDecoder(Writer w, CNode node, String parent, boolean root) throws IOException {
        String tmpName = getTypeName(node, false);
        String typeName = root ? getInternalClassName(node) : tmpName;
        String indent = "    ";
        if (root) {
            w.write(""
                    + typeName + "::" + typeName + "(const ::config::ConfigDataBuffer & __buffer)\n"
                    + "{\n");
            w.write(indent + "const vespalib::Slime & __slime(__buffer.slimeObject());\n");
            w.write(indent + "vespalib::slime::Inspector & __croot = __slime.get();\n");
            w.write(indent + "vespalib::slime::Inspector & __inspector = __croot[\"configPayload\"];\n");
        } else {
            w.write(""
                    + parent + typeName + "(const vespalib::slime::Inspector & __inspector)\n"
                    + "{\n");
        }

        for (CNode child : node.getChildren()) {
            String childName = getIdentifier(child.getName());
            String childType = getTypeName(child, false);
            String inspectorLine = "__inspector[\"" + child.getName() + "\"][\"value\"]";
            if (child.isArray) {
                w.write(indent + "for (size_t __i = 0; __i < " + inspectorLine + ".children(); __i++) {\n");
                w.write(indent + "    " + childName + ".push_back(");
                if (child instanceof LeafCNode.EnumLeaf) {
                    String repType = slimeTypeMap.get("enum");
                    w.write("get" + childType + "(" + inspectorLine + "[__i][\"value\"].as" + repType + "().make_string())");
                } else if (child instanceof LeafCNode) {
                    String type = ((LeafCNode) child).getType();
                    String repType = slimeTypeMap.get(type);
                    if ("String".equals(repType)) {
                        w.write("" + inspectorLine + "[__i][\"value\"].as" + repType + "().make_string()");
                    } else {
                        w.write("" + inspectorLine + "[__i][\"value\"].as" + repType + "()");
                    }
                } else {
                    w.write(childType + "(" + inspectorLine + "[__i][\"value\"])");
                }
                w.write(");\n");
                w.write(indent + "}\n");
            } else if (child.isMap) {
                w.write(indent + "for (size_t __i = 0; __i < " + inspectorLine + ".children(); __i++) {\n");
                w.write(indent + "    " + childName + "[" + inspectorLine + "[__i][\"key\"].asString().make_string()] = ");
                if (child instanceof LeafCNode.EnumLeaf) {
                    String repType = slimeTypeMap.get("enum");
                    w.write("get" + childType + "(" + inspectorLine + "[__i][\"value\"].as" + repType + "().make_string())");
                } else if (child instanceof LeafCNode) {
                    String type = ((LeafCNode) child).getType();
                    String repType = slimeTypeMap.get(type);
                    if ("String".equals(repType)) {
                        w.write("" + inspectorLine + "[__i][\"value\"].as" + repType + "().make_string()");
                    } else {
                        w.write("" + inspectorLine + "[__i][\"value\"].as" + repType + "()");
                    }
                } else {
                    w.write(childType + "(" + inspectorLine + "[__i][\"value\"])");
                }
                w.write(";\n");
                w.write(indent + "}\n");
            } else {
                w.write(indent + childName + " = ");
                if (child instanceof LeafCNode.EnumLeaf) {
                    String repType = slimeTypeMap.get("enum");
                    w.write("get" + childType + "(" + inspectorLine + ".as" + repType + "().make_string())");
                } else if (child instanceof LeafCNode) {
                    String type = ((LeafCNode) child).getType();
                    String repType = slimeTypeMap.get(type);
                    if ("String".equals(repType)) {
                        w.write("" + inspectorLine + ".as" + repType + "().make_string()");
                    } else {
                        w.write("" + inspectorLine + ".as" + repType + "()");
                    }
                } else {
                    w.write(childType + "(" + inspectorLine + ")");
                }
                w.write(";\n");
            }
        }
        w.write("}\n\n");
    }

    public void writeSlimeConstructor(Writer w, CNode node, String parent, boolean root) throws IOException {
        String tmpName = getTypeName(node, false);
        String typeName = root ? getInternalClassName(node) : tmpName;
        String indent = "    ";
        if (root) {
            w.write(""
                    + typeName + "::" + typeName + "(const ::config::ConfigPayload & __payload)\n"
                    + "{\n");
        } else {
            w.write(""
                    + parent + typeName + "(const ::config::ConfigPayload & __payload)\n"
                    + "{\n");
        }
        w.write(indent + "const vespalib::slime::Inspector & __inspector(__payload.get());\n");
        for (CNode child : node.getChildren()) {
            String childName = getIdentifier(child.getName());
            String childType = getTypeName(child, false);
            String childInspector = "__inspector[\"" + child.getName() + "\"]";
            if (child.isArray) {
                String inserterName = "__" + childName + "Inserter";
                w.write(indent + "::config::internal::VectorInserter<" + childType);
                if (child instanceof LeafCNode.EnumLeaf) {
                    w.write(", Internal" + childType + "Converter");
                }
                w.write("> " + inserterName + "(" + childName + ");\n");
                w.write(indent + childInspector + ".traverse(" + inserterName + ");\n");
            } else if (child.isMap) {
                String inserterName = "__" + childName + "Inserter";
                w.write(indent + "::config::internal::MapInserter<" + childType);
                if (child instanceof LeafCNode.EnumLeaf) {
                    w.write(", Internal" + childType + "Converter");
                }
                w.write("> " + inserterName + "(" + childName + ");\n");
                w.write(indent + childInspector + ".traverse(" + inserterName + ");\n");
            } else {
                w.write(indent + childName + " = ");
                if (child instanceof LeafCNode.EnumLeaf) {
                    w.write("Internal" + childType + "Converter");
                } else {
                    w.write("::config::internal::ValueConverter<" + childType + ">");
                }
                if (child instanceof LeafCNode && ((LeafCNode) child).getDefaultValue() != null) {
                    LeafCNode leaf = (LeafCNode) child;
                    String defaultValue = getDefaultValue(leaf);
                    if (leaf.getType().equals("enum")) {
                        defaultValue = getTypeName(leaf, false) + "::" + defaultValue;
                    }
                    w.write("()(" + childInspector + ", " + defaultValue + ");\n");
                } else if (child instanceof InnerCNode) {
                    w.write("()(" + childInspector + ");\n");
                } else {
                    w.write("()(\"" + child.getName() + "\", " + childInspector + ");\n");
                }
            }
        }
        w.write("}\n\n");
    }

    void writeBodyFooter(Writer w, CNode root) throws IOException {
        w.write("} // namespace internal\n\n");
        writeNameSpaceEnd(w, generateCppNameSpace(root));
    }

    String getDefaultValue(LeafCNode leaf) {
        String defaultVal = leaf.getDefaultValue().getStringRepresentation();
        if (leaf.getType().equals("string") && defaultVal.equals("null"))
                throw new CodegenRuntimeException("Default value null not allowed for C++ config");
        if (leaf.getType().equals("long") && "-9223372036854775808".equals(defaultVal)) {
            return "LONG_MIN";
        } else if (leaf.getType().equals("int") && "-2147483648".equals(defaultVal)) {
            return "INT_MIN";
        } else {
            return defaultVal;
        }
    }

}
