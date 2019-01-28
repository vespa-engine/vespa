// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.util;

import com.yahoo.collections.Tuple2;
import com.yahoo.config.codegen.CNode;
import com.yahoo.io.HexDump;
import com.yahoo.io.IOUtils;
import com.yahoo.net.HostName;
import com.yahoo.slime.JsonFormat;
import com.yahoo.text.Utf8;
import com.yahoo.text.Utf8Array;
import com.yahoo.vespa.config.*;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities for mangling config text, finding md5sums, version numbers in .def files etc.
 */
public class ConfigUtils {
    /* Patterns used for finding ranges in config definitions */
    private static final Pattern intPattern = Pattern.compile(".*int.*range.*");
    private static final Pattern doublePattern = Pattern.compile(".*double.*range.*");
    private static final Pattern spaceBeforeCommaPatter = Pattern.compile("\\s,");
    public static final String intFormattedMax = new DecimalFormat("#.#").format(0x7fffffff);
    public static final String intFormattedMin = new DecimalFormat("#.#", new DecimalFormatSymbols(Locale.ENGLISH)).format(-0x80000000);
    public static final String doubleFormattedMax = new DecimalFormat("#.#").format(1e308);
    public static final String doubleFormattedMin = new DecimalFormat("#.#", new DecimalFormatSymbols(Locale.ENGLISH)).format(-1e308);

    /**
     * Computes Md5 hash of a list of strings. The only change to input lines before
     * computing md5 is to skip empty lines.
     *
     * @param payload a config payload
     * @return the Md5 hash of the list, with lowercase letters
     */
    public static String getMd5(ConfigPayload payload) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            payload.serialize(baos, new JsonFormat(true));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        MessageDigest md5 = getMd5Instance();
        md5.update(baos.toByteArray());
        return HexDump.toHexString(md5.digest()).toLowerCase();
    }

    /**
     * Computes Md5 hash of a list of strings. The only change to input lines before
     * computing md5 is to skip empty lines.
     *
     * @param lines A list of lines
     * @return the Md5 hash of the list, with lowercase letters
     */
    public static String getMd5(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            // Remove empty lines
            line = line.trim();
            if (line.length() > 0) {
                sb.append(line).append("\n");
            }
        }
        MessageDigest md5 = getMd5Instance();
        md5.update(Utf8.toBytes(sb.toString()));
        return HexDump.toHexString(md5.digest()).toLowerCase();
    }

    /**
     * Computes Md5 hash of a string.
     *
     * @param input the input String
     * @return the Md5 hash of the input, with lowercase letters
     */
    public static String getMd5(String input) {
        MessageDigest md5 = getMd5Instance();
        md5.update(IOUtils.utf8ByteBuffer(input));
        return HexDump.toHexString(md5.digest()).toLowerCase();
    }

    public static String getMd5(Utf8Array input) {
        MessageDigest md5 = getMd5Instance();
        md5.update(input.getBytes());
        return HexDump.toHexString(md5.digest()).toLowerCase();
    }

    private static MessageDigest getMd5Instance() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    /**
     * Replaces sequences of spaces with 1 space, unless inside quotes. Public for testing;
     * @param str String to strip spaces from
     * @return String with spaces stripped
     */
    public static String stripSpaces(String str) {
        StringBuilder ret = new StringBuilder("");
        boolean inQuotes = false;
        boolean inSpaceSequence = false;
        for (char c : str.toCharArray()) {
            if (Character.isWhitespace(c)) {
                if (inQuotes) {
                    ret.append(c);
                    continue;
                }
                if (!inSpaceSequence) {
                    // start of space sequence
                    inSpaceSequence=true;
                    ret.append(" ");
                }
            } else {
                if (inSpaceSequence) {
                    inSpaceSequence=false;
                }
                if (c=='\"') {
                    inQuotes=!inQuotes;
                }
                ret.append(c);
            }
        }
        return ret.toString();
    }

    /**
     * Computes Md5 hash of a list of strings with the contents of a def-file.
     *
     * Each string is normalized according to the
     * rules of Vespa config definition files before they are used:
     * <ol>
     * <li>Remove trailing space.<li>
     * <li>Remove comment lines.</li>
     * <li>Remove trailing comments, and spaces before trailing comments.</li>
     * <li>Remove empty lines</li>
     * <li>Remove 'version=&lt;version-number&gt;'</li>
     * </ol>
     *
     * @param lines  A list of lines constituting a def-file
     * @return the Md5 hash of the list, with lowercase letters
     */
    public static String getDefMd5(List<String> lines) {
        List<String> linesCopy = new ArrayList<>(lines);
        for (Iterator<String> it=linesCopy.iterator(); it.hasNext(); ) {
            String line = it.next().trim();
            if (! line.startsWith("#") && ! line.equals("")) {
                if (line.startsWith("version")) {
                    it.remove();
                }
                // Quit upon 'version', or first line with real content since 'version' cannot occur after that
                break;
            }
        }

        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String line : linesCopy) {
            // Normalize line, like it's done in make-config-preproc.pl
            line = line.trim();
            // The perl script does stuff like this:
            Matcher m = intPattern.matcher(line);
            if (m.matches()) {
                line = line.replaceFirst("\\[,", "[" + intFormattedMin + ",");
                line = line.replaceFirst(",\\]", "," + intFormattedMax + "]");
            }
            m = doublePattern.matcher(line);
            if (m.matches()) {
                line = line.replaceFirst("\\[,", "[" + doubleFormattedMin + ",");
                line = line.replaceFirst(",\\]", "," + doubleFormattedMax + "]");
            }
            if (line.contains("#")) {
                line = line.substring(0, line.indexOf("#"));
                line = line.trim();  // Remove space between "real" end of line and a trailing comment
            }
            if (line.length() > 0) {
                line = stripSpaces(line);
                m  = spaceBeforeCommaPatter.matcher(line);
                line = m.replaceAll(",");   // Remove space before comma (for enums)
                sb.append(line).append("\n");
            }
        }
        md5.update(Utf8.toBytes(sb.toString()));
        return HexDump.toHexString(md5.digest()).toLowerCase();
    }

    /**
     * Finds the def version from a reader for a def-file. Returns "" (empty string)
     * if no version was found.
     *
     * @param in A reader to a def-file
     * @return version of the def-file, or "" (empty string) if no version was found
     */
    public static String getDefVersion(Reader in) {
        return getDefKeyword(in, "version");
    }

    /**
     * Finds the def package or namespace from a reader for a def-file. Returns "" (empty string)
     * if no package or namespace was found. If both package and namespace are declared in the def
     * file, the package is returned.
     *
     * @param in A reader to a def-file
     * @return namespace of the def-file, or "" (empty string) if no namespace was found
     */
    public static String getDefNamespace(Reader in) {
        List<String> defLines = getDefLines(in);
        String defPackage = getDefKeyword(defLines, "package");
        if (! defPackage.isEmpty()) return defPackage;
        return getDefKeyword(defLines, "namespace");
    }

    /**
     * Finds the value of the keyword in <code>keyword</code> from a reader for a def-file.
     * Returns "" (empty string) if no value for keyword was found.
     *
     * @param in  A reader to a def-file
     * @return value of keyword, or "" (empty string) if no line matching keyword was found
     */
    public static String getDefKeyword(Reader in, String keyword) {
        return getDefKeyword(getDefLines(in), keyword);
    }

    private static String getDefKeyword(List<String> defLines, String keyword) {
        for (String line : defLines) {
            if (line.startsWith(keyword)) {
                String[] v = line.split("=");
                return v[1].trim();
            }
        }
        return "";
    }

    private static List<String> getDefLines(Reader in) {
        if (null == in) {
            throw new IllegalArgumentException("Null reader.");
        }
        List<String> defLines = new ArrayList<>();
        LineNumberReader reader;
        try {
            if (in instanceof LineNumberReader) {
                reader = (LineNumberReader) in;
            } else {
                reader = new LineNumberReader(in);
            }
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.startsWith("#") && !line.equals("")) {
                    defLines.add(line);
                }
            }
            reader.close();
        } catch (IOException e) {
            throw new RuntimeException("IOException", e);
        }
        return defLines;
    }

    /**
     * Finds the name and version from a string with "name,version".
     * If no name is given, the first part of the tuple will be the empty string
     * If no version is given, the second part of the tuple will be the empty string
     *
     * @param nameCommaVersion A string consisting of "name,version"
     * @return a Tuple2 with first item being name and second item being version
     */
    public static Tuple2<String, String> getNameAndVersionFromString(String nameCommaVersion) {
        String[] av = nameCommaVersion.split(",");
        return new Tuple2<>(av[0], av.length >= 2 ? av[1] : "");
    }

    /**
     * Finds the name and namespace from a string with "namespace.name".
     * namespace may contain dots.
     * If no namespace is given (".name" or just "name"), the second part of the tuple will be the empty string
     * If no name is given, the first part of the tuple will be the empty string
     *
     * @param nameDotNamespace A string consisting of "namespace.name"
     * @return a Tuple2 with first item being name and second item being namespace
     */
    public static Tuple2<String, String> getNameAndNamespaceFromString(String nameDotNamespace) {
        if (!nameDotNamespace.contains(".")) {
            return new Tuple2<>(nameDotNamespace, "");
        }
        String name = nameDotNamespace.substring(nameDotNamespace.lastIndexOf(".") + 1);
        String namespace = nameDotNamespace.substring(0, nameDotNamespace.lastIndexOf("."));
        return new Tuple2<>(name, namespace);
    }

    /**
     * Creates a ConfigDefinitionKey based on a string with namespace, name and version
     * (e.g. Vespa's own config definitions in $VESPA_HOME/share/vespa/configdefinitions)
     *
     * @param input A string consisting of "namespace.name.version"
     * @return a ConfigDefinitionKey
     */
    @SuppressWarnings("deprecation")
    public static ConfigDefinitionKey getConfigDefinitionKeyFromString(String input) {
        final String name;
        final String namespace;
        if (!input.contains(".")) {
            name = input;
            namespace = "";
        } else if (input.lastIndexOf(".") == input.indexOf(".")) {
            Tuple2<String, String> tuple = ConfigUtils.getNameAndNamespaceFromString(input);
            boolean containsVersion = false;
            for (int i=0; i < tuple.first.length(); i++) {
               if (Character.isDigit(tuple.first.charAt(i))) {
                   containsVersion = true;
                   break;
               }
            }
            if (containsVersion) {
                name = tuple.second;
                namespace = "";
            } else {
                name = tuple.first;
                namespace = tuple.second;
            }
        } else {
            Tuple2<String, String> tuple = ConfigUtils.getNameAndNamespaceFromString(input);

            String tempName = tuple.second;
            tuple = ConfigUtils.getNameAndNamespaceFromString(tempName);
            name = tuple.first;
            namespace = tuple.second;
        }
        return new ConfigDefinitionKey(name, namespace);
    }

    /**
     * Creates a ConfigDefinitionKey from a string for the name of a node in ZooKeeper
     * that holds a config definition
     *
     * @param nodeName name of  a node in ZooKeeper that holds a config definition
     * @return a ConfigDefinitionKey
     */
    @SuppressWarnings("deprecation")
    public static ConfigDefinitionKey createConfigDefinitionKeyFromZKString(String nodeName) {
        final String name;
        final String namespace;
        if (nodeName.contains(".")) {
            Tuple2<String, String> tuple = ConfigUtils.getNameAndVersionFromString(nodeName);
            String tempName = tuple.first; // includes namespace
            tuple = ConfigUtils.getNameAndNamespaceFromString(tempName);
            name = tuple.first;
            namespace = tuple.second;
        } else {
            Tuple2<String, String> tuple = ConfigUtils.getNameAndVersionFromString(nodeName);
            name = tuple.first;
            namespace = "";
        }
        return new ConfigDefinitionKey(name, namespace);
    }


    /**
     * Creates a ConfigDefinitionKey from a file by reading the file and parsing
     * contents for namespace. Name and from filename, but the filename may be prefixed
     * with the namespace (if two def files has the same name for instance).
     *
     * @param file a config definition file
     * @return a ConfigDefinitionKey
     */
    public static ConfigDefinitionKey createConfigDefinitionKeyFromDefFile(File file) throws IOException {
        String[] fileName = file.getName().split("\\.");
        assert(fileName.length >= 2);
        String name = fileName[fileName.length - 2];
        byte[] content = IOUtils.readFileBytes(file);

        return createConfigDefinitionKeyFromDefContent(name, content);
    }

    /**
     * Creates a ConfigDefinitionKey from a name and the content of a config definition
     *
     * @param name the name of the config definition
     * @param content content of a config definition
     * @return a ConfigDefinitionKey
     */
    @SuppressWarnings("deprecation")
    public static ConfigDefinitionKey createConfigDefinitionKeyFromDefContent(String name, byte[] content) {
        String namespace = ConfigUtils.getDefNamespace(new StringReader(Utf8.toString(content)));
        if (namespace.isEmpty()) {
            namespace = CNode.DEFAULT_NAMESPACE;
        }
        return new ConfigDefinitionKey(name, namespace);
    }


    /**
     * Escapes a config value according to the cfg format.
     * @param input the string to escape
     * @return the escaped string
     */
    public static String escapeConfigFormatValue(String input) {
        if (input == null) {
            return "null";
        }
        StringBuilder outputBuf = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            if (input.charAt(i) == '\\') {
                outputBuf.append("\\\\");         // backslash is escaped as: \\
            } else if (input.charAt(i) == '"') {
                outputBuf.append("\\\"");         // double quote is escaped as: \"
            } else if (input.charAt(i) == '\n') {
                outputBuf.append("\\n");          // newline is escaped as: \n
            } else if (input.charAt(i) == 0) {
                // XXX null byte is probably not a good idea anyway
                System.err.println("WARNING: null byte in config value");
                outputBuf.append("\\x00");
            } else {
                // all other characters are output as-is
                outputBuf.append(input.charAt(i));
            }
        }
        return outputBuf.toString();
    }


    public static String getDefMd5FromRequest(String defMd5, List<String> defContent) {
        if ((defMd5 == null || defMd5.isEmpty()) && defContent != null) {
            return ConfigUtils.getDefMd5(defContent);
        } else {
            return defMd5;
        }
    }

    public static String getCanonicalHostName() {
        return HostName.getLocalhost();
    }

    /**
     * Loop through values and return the first one that is set and non-empty.
     *
     * @param defaultValue The default value to use if no environment variables are set.
     * @param envVars one or more environment variable strings
     * @return a String with the value of the environment variable
     */
    public static String getEnvValue(String defaultValue, String ... envVars) {
        String value = null;
        for (String envVar : envVars) {
            if (value == null || value.isEmpty()) {
                value = envVar;
            }
        }
        return (value == null || value.isEmpty()) ? defaultValue : value;
    }

    public static boolean isGenerationNewer(long newGen, long oldGen) {
        return (oldGen < newGen) || (newGen == 0);
    }
}
