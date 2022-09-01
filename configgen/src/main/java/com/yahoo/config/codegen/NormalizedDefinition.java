// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.codegen;

import java.io.*;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.text.DecimalFormat;
import java.security.MessageDigest;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 *
 * Does normalizing (removing comments, trimming whitespace etc.) and calculation of md5sum
 * of config definitions
 *
 * @author hmusum
 */
public class NormalizedDefinition {

    // Patterns used for finding ranges in config definitions
    private static final Pattern intPattern = Pattern.compile(".*int.*range.*");
    private static final Pattern doublePattern = Pattern.compile(".*double.*range.*");
    private final MessageDigest md5;

    String defMd5 = null;
    List<String> normalizedContent = null;

    public NormalizedDefinition() {
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("Unable to create MD5 digest", e);
        }
        normalizedContent = new ArrayList<>();
    }

    public NormalizedDefinition normalize(BufferedReader reader) throws IOException {
        String s;
        List<String> input = new ArrayList<>();
        while ((s = reader.readLine()) != null) {
            String normalized = normalize(s);
            if (normalized.length() > 0) {
                input.add(normalized);
            }
        }
        normalizedContent = input;
        return this;
    }

    /**
     * Normalizes a config definition line. Each string is normalized according to the
     * rules of config and definition files before they are used:
     * <ul>
     * <li>Remove trailing space.<li>
     * <li>Remove trailing comments, and spaces before trailing comments.</li>
     * <li>Remove empty lines</li>
     * <li>Keep comment lines</li>
     * </ul>
     * The supplied list is changed in-place
     *
     * @param line a config definition line
     * @return a normalized config definition line
     */
    public static String normalize(String line) {
        //System.out.println("before line=" + line + ";");
        // Normalize line
        line = line.trim();
        Matcher m = intPattern.matcher(line);
        if (m.matches()) {
            String formattedMax = new DecimalFormat("#.#").format(0x7fffffff);
            String formattedMin = new DecimalFormat("#.#").format(-0x80000000);
            line = line.replaceFirst("\\[,", "["+formattedMin+",");
            line = line.replaceFirst(",\\]", ","+formattedMax+"]");
        }
        m = doublePattern.matcher(line);
        if (m.matches()) {
            String formattedMax = new DecimalFormat("#.#").format(1e308);
            String formattedMin = new DecimalFormat("#.#").format(-1e308);
            line = line.replaceFirst("\\[,", "["+formattedMin+",");
            line = line.replaceFirst(",\\]", ","+formattedMax+"]");
        }
        line = removeComment(line);
        if (!line.isEmpty()) {
            line = stripSpaces(line);
            line = line.replaceAll("\\s,", ",");  // Remove space before comma (for enums)
            line += "\n";
        }
        //System.out.println("after line=" + line + ";");
        return line;
    }

    // Removes comment char and text after it, unless comment char is inside a string
    // Keeps comment lines (lines that start with #)
    private static String removeComment(String line) {
        int index = line.indexOf("#");
        if (!line.contains("#") || index == 0) return line;

        int firstQuote = line.indexOf("\"");
        if (firstQuote > 0) {
            int secondQuote = line.indexOf("\"", firstQuote + 1);
            if (index > secondQuote) {
                line = line.substring(0, index);
                line = line.trim();
            }
        } else  {
            line = line.substring(0, index);
            line = line.trim();
        }

        return line;
    }

    public void addNormalizedLine(String line) {
        normalizedContent.add(line);
    }

    public String generateMd5Sum() {
        for (String line : normalizedContent) {
            String s = normalize(line);
            if (!s.isEmpty()) {
                md5.update(toBytes(s));
            }
        }
        defMd5 = toHexString(md5.digest()).toLowerCase();
        //System.out.println("md5=" + defMd5) ;
        return defMd5;
    }


    // The two methods below are copied from vespajlib (com.yahoo.text.Utf8 and com.yahoo.io.HexDump)
    // since configgen cannot depend on any other modules (at least not as it is done now)
    public static byte[] toBytes(String str) {
        Charset charset = Charset.forName("utf-8");

        ByteBuffer b = charset.encode(str);
        byte[] result = new byte[b.remaining()];
        b.get(result);
        return result;
    }

    private String toHexString(byte[] bytes) {
        StringBuilder sb =  new StringBuilder(bytes.length * 2);
        for (byte aByte : bytes) {
            sb.append(String.format("%02x", aByte));
        }
        return sb.toString();
    }

    /**
     * Replaces sequences of spaces with 1 space, unless inside quotes. Public for testing.
     *
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

    public List<String> getNormalizedContent() {
        return normalizedContent;
    }

    @Override
    public String toString() {
    	StringBuilder builder = new StringBuilder();
    	for (String line : normalizedContent) {
    		builder.append(line.replace("\"", "\\\""));
    		builder.append("\\n\\\n");
    	}
    	return builder.toString();
    }

    public String getDefMd5() {
        return defMd5;
    }

}
