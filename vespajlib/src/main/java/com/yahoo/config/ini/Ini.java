package com.yahoo.config.ini;

import com.yahoo.yolean.Exceptions;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Scanner;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Basic <a href="https://en.wikipedia.org/wiki/INI_file">INI file</a> parser.
 *
 * <p>Supported syntax:</p>
 *
 * <ul>
 *  <li>Sections. Surrounded with '[' and ']'</li>
 *  <li>Optional quoting of values. When quoted, the quote character '"' can be escaped with '\'</li>
 *  <li>Comments, separate and in-line. Indicated with leading ';' or '#'</li>
 * </ul>
 *
 * <p>Behaviour:</p>
 *
 * <ul>
 *  <li>Leading and trailing whitespace is always ignored if the value is unquoted</li>
 *  <li>Sections are sorted in alphabetic order. The same goes for keys within a section</li>
 *  <li>Empty string in the parsed Map holds section-less config keys</li>
 *  <li>Duplicated keys within the same section is an error</li>
 *  <li>Parsing discards comments</li>
 *  <li>No limitations on section or key names</li>
 * </ul>
 *
 * @param entries Entries of the INI file, grouped by section.
 *
 * @author mpolden
 */
public record Ini(SortedMap<String, SortedMap<String, String>> entries) {

    private static final char ESCAPE_C = '\\';
    private static final char QUOTE_C = '"';
    private static final String QUOTE = String.valueOf(QUOTE_C);

    public Ini {
        var copy = new TreeMap<>(entries);
        copy.replaceAll((k, v) -> Collections.unmodifiableSortedMap(new TreeMap<>(copy.get(k))));
        entries = Collections.unmodifiableSortedMap(copy);
    }

    /** Write the text representation of this to given output */
    public void write(OutputStream output) {
        PrintStream printer = new PrintStream(output, true);
        entries.forEach((section, sectionEntries) -> {
            if (!section.isEmpty()) {
                printer.printf("[%s]\n", section);
            }
            sectionEntries.forEach((key, value) -> {
                printer.printf("%s = %s\n", key, quote(value));
            });
            if (!section.equals(entries.lastKey())) {
                printer.println();
            }
        });
    }

    /** Parse an INI configuration from given input */
    public static Ini parse(InputStream input) {
        SortedMap<String, SortedMap<String, String>> entries = new TreeMap<>();
        Scanner scanner = new Scanner(input, StandardCharsets.UTF_8);
        String section = "";
        int lineNum = 0;
        while (scanner.hasNextLine()) {
            lineNum++;
            String line = scanner.nextLine().trim();
            // Blank line
            if (line.isEmpty()) {
                continue;
            }
            // Comment
            if (isComment(line)) {
                continue;
            }
            // Section
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1);
                continue;
            }
            // Key-value entry
            try {
                Entry entry = Entry.parse(line);
                entries.putIfAbsent(section, new TreeMap<>());
                String prevValue = entries.computeIfAbsent(section, (k) -> new TreeMap<>())
                                          .put(entry.key, entry.value);
                if (prevValue != null) {
                    throw new IllegalArgumentException("Key '" + entry.key + "' duplicated in section '" +
                                                       section + "'");
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid entry on line " + lineNum + ": '" + line + "': " +
                                                   Exceptions.toMessageString(e));
            }
        }
        return new Ini(entries);
    }

    private static boolean isComment(String s) {
        return s.startsWith(";") || s.startsWith("#");
    }

    private static boolean requiresQuoting(String s) {
        return s.isEmpty() || s.contains(QUOTE) || !s.equals(s.trim());
    }

    private static boolean unescapedQuoteAt(int index, String s) {
        return s.charAt(index) == QUOTE_C && (index == 0 || s.charAt(index - 1) != ESCAPE_C);
    }

    private static String quote(String s) {
        if (!requiresQuoting(s)) return s;
        StringBuilder sb = new StringBuilder();
        sb.append(QUOTE);
        for (int i = 0; i < s.length(); i++) {
            if (unescapedQuoteAt(i, s)) {
                sb.append(ESCAPE_C);
            }
            sb.append(s.charAt(i));
        }
        sb.append(QUOTE);
        return sb.toString();
    }

    private record Entry(String key, String value) {

        static Entry parse(String s) {
            int equalIndex = s.indexOf('=');
            if (equalIndex < 0) throw new IllegalArgumentException("Expected key=[value]");
            String key = s.substring(0, equalIndex).trim();
            String value = s.substring(equalIndex + 1).trim();
            return new Entry(key, dequote(value));
        }

        private static String dequote(String s) {
            boolean quoted = s.startsWith(QUOTE);
            int end = s.length();
            boolean closeQuote = false;
            for (int i = 0; i < s.length(); i++) {
                closeQuote = quoted && i > 0 && unescapedQuoteAt(i, s);
                boolean startComment = !quoted && isComment(String.valueOf(s.charAt(i)));
                if (closeQuote || startComment) {
                    end = i;
                    if (quoted && end < s.length() - 1) {
                        String trailing = s.substring(end + 1).trim();
                        if (!isComment(trailing)) {
                            throw new IllegalArgumentException("Additional character(s) after end quote at column " + end);
                        }
                    }
                    break;
                }
            }
            if (quoted && !closeQuote) {
                throw new IllegalArgumentException("Missing closing quote");
            }
            int start = quoted ? 1 : 0;
            String value = s.substring(start, end);
            return quoted ? value : value.trim();
        }

    }

}
