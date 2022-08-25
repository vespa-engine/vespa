// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.util;

import com.yahoo.collections.Tuple2;
import com.yahoo.io.HexDump;
import com.yahoo.io.IOUtils;
import com.yahoo.net.HostName;
import com.yahoo.slime.JsonFormat;
import com.yahoo.text.AbstractUtf8Array;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.ConfigPayload;
import net.jpountz.xxhash.XXHash64;
import net.jpountz.xxhash.XXHashFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities for mangling config text, finding checksums, finding name and namespace in .def files etc.
 */
public class ConfigUtils {

    /* Patterns used for finding ranges in config definitions */
    private static final Pattern intPattern = Pattern.compile(".*int.*range.*");
    private static final Pattern doublePattern = Pattern.compile(".*double.*range.*");
    private static final Pattern spaceBeforeCommaPatter = Pattern.compile("\\s,");
    private static final Pattern packageDirectivePattern = Pattern.compile("^\\s*package\\s*=(.*)$");
    private static final Pattern namespaceDirectivePattern = Pattern.compile("^\\s*namespace\\s*=(.*)$");
    private static final Pattern packagePattern = Pattern.compile("^(([a-z][a-z0-9_]*)+([.][a-z][a-z0-9_]*)*)$");
    private static final String intFormattedMax = new DecimalFormat("#.#").format(0x7fffffff);
    private static final String intFormattedMin = new DecimalFormat("#.#", new DecimalFormatSymbols(Locale.ENGLISH)).format(-0x80000000);
    private static final String doubleFormattedMax = new DecimalFormat("#.#").format(1e308);
    private static final String doubleFormattedMin = new DecimalFormat("#.#", new DecimalFormatSymbols(Locale.ENGLISH)).format(-1e308);

    public static String getMd5(String input) {
        return getMd5(ByteBuffer.wrap(input.getBytes(StandardCharsets.UTF_8)));
    }

    public static String getMd5(AbstractUtf8Array input) {
        return getMd5(input.wrap());
    }

    private static String getMd5(ByteBuffer input) {
        MessageDigest md5 = getMd5Instance();
        md5.update(input);
        return HexDump.toHexString(md5.digest()).toLowerCase();
    }

    private static MessageDigest getMd5Instance() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Could not get md5 instance");
        }
    }

    public static String getXxhash64(AbstractUtf8Array input) {
        return getXxhash64(input.wrap());
    }

    public static String getXxhash64(ByteBuffer input) {
        XXHash64 hasher = XXHashFactory.fastestInstance().hash64();
        return Long.toHexString(hasher.hash(input, 0)).toLowerCase();
    }

    @SuppressWarnings("unused") // Used by config integration test in system-test module
    public static String getXxhash64(ConfigPayload payload) {
        return getXxhash64(getByteBuffer(payload));
    }

    private static ByteBuffer getByteBuffer(ConfigPayload payload) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            payload.serialize(baos, new JsonFormat(true));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return ByteBuffer.wrap(baos.toByteArray());
    }

    /**
     * Replaces sequences of spaces with 1 space, unless inside quotes. Public for testing;
     *
     * @param str String to strip spaces from
     * @return String with spaces stripped
     */
    public static String stripSpaces(String str) {
        StringBuilder ret = new StringBuilder();
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
                    inSpaceSequence = true;
                    ret.append(" ");
                }
            } else {
                if (inSpaceSequence) {
                    inSpaceSequence = false;
                }
                if (c == '\"') {
                    inQuotes = !inQuotes;
                }
                ret.append(c);
            }
        }
        return ret.toString();
    }

    /**
     * Computes Md5 hash of a list of strings with the contents of a def-file.
     * <p>
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
     * @param lines A list of lines constituting a def-file
     * @return the Md5 hash of the list, with lowercase letters
     */
    public static String getDefMd5(List<String> lines) {
        List<String> linesCopy = new ArrayList<>(lines);
        for (Iterator<String> it = linesCopy.iterator(); it.hasNext(); ) {
            String line = it.next().trim();
            if (! line.startsWith("#") && ! line.equals("")) {
                if (line.startsWith("version")) {
                    it.remove();
                }
                // Quit upon 'version', or first line with real content since 'version' cannot occur after that
                break;
            }
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
                m = spaceBeforeCommaPatter.matcher(line);
                line = m.replaceAll(",");   // Remove space before comma (for enums)
                sb.append(line).append("\n");
            }
        }
        return getMd5(sb.toString());
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
        String declaredPackage = getDirective(defLines, packageDirectivePattern);
        String declaredNamespace = getDirective(defLines, namespaceDirectivePattern);
        return declaredPackage != null ? declaredPackage : declaredNamespace != null ? declaredNamespace : "";
    }

    static String getDirective(List<String> defLines, Pattern directivePattern) {
        Matcher matcher;
        for (String defLine : defLines) {
            if ((matcher = directivePattern.matcher(defLine)).matches()) {
                if ((matcher = packagePattern.matcher(matcher.group(1))).matches())
                    return matcher.group(1);
                else
                    throw new IllegalArgumentException("package (or namespace) must consist of one or more segments joined by single dots (.), " +
                                                       "each starting with a lowercase letter (a-z), and then containing one or more " +
                                                       "lowercase letters (a-z), digits (0-9), or underscores (_)");
            }
        }
        return null;
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
     * Finds the name and namespace part from a string "name.namespace,version", which
     * is how it is serialized in zookeeper (versions is always empty)
     *
     * @param nameCommaVersion A string consisting of "name.namespace,version" or "name.namespace,"
     * @return a string with name.namespace
     */
    private static String getNameFromSerializedString(String nameCommaVersion) {
        String[] av = nameCommaVersion.split(",");
        return av[0];
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
     * Creates a ConfigDefinitionKey from a string for the name of a node in ZooKeeper
     * that holds a config definition
     *
     * @param nodeName name of  a node in ZooKeeper that holds a config definition
     * @return a ConfigDefinitionKey
     */
    public static ConfigDefinitionKey createConfigDefinitionKeyFromZKString(String nodeName) {
        final String name;
        final String namespace;
        String tempName = ConfigUtils.getNameFromSerializedString(nodeName); // includes namespace
        Tuple2<String, String> tuple = ConfigUtils.getNameAndNamespaceFromString(tempName);
        name = tuple.first;
        namespace = tuple.second;
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
        assert (fileName.length >= 2);
        String name = fileName[fileName.length - 2];
        byte[] content = IOUtils.readFileBytes(file);

        return createConfigDefinitionKeyFromDefContent(name, content);
    }

    /**
     * Creates a ConfigDefinitionKey from a name and the content of a config definition
     *
     * @param name    the name of the config definition
     * @param content content of a config definition
     * @return a ConfigDefinitionKey
     */
    static ConfigDefinitionKey createConfigDefinitionKeyFromDefContent(String name, byte[] content) {
        String namespace = ConfigUtils.getDefNamespace(new StringReader(Utf8.toString(content)));
        return new ConfigDefinitionKey(name, namespace);
    }

    /**
     * Escapes a config value according to the cfg format.
     *
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
     * @param envVars      one or more environment variable strings
     * @return a String with the value of the environment variable
     */
    public static String getEnvValue(String defaultValue, String... envVars) {
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
