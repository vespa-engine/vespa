// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;

import static java.lang.Character.*;

// Output each character Java consider to be a letter on its own line.
// This is used as input to the other implementations of lowercasing we
// use in Java and C++.

// With option "lower", the entire list is lowercased using Java
// standard implementation.  This is used as the reference result so
// we can compare where our own implementations behave differently.

// Other options are available for exploration and future work.

public class Letters {

    static String typeName(int type) {
        return switch(type) {
            case COMBINING_SPACING_MARK      -> "COMBINING_SPACING_MARK";
            case CONNECTOR_PUNCTUATION       -> "CONNECTOR_PUNCTUATION";
            case CONTROL                     -> "CONTROL";
            case CURRENCY_SYMBOL             -> "CURRENCY_SYMBOL";
            case DASH_PUNCTUATION            -> "DASH_PUNCTUATION";
            case DECIMAL_DIGIT_NUMBER        -> "DECIMAL_DIGIT_NUMBER";
            case ENCLOSING_MARK              -> "ENCLOSING_MARK";
            case END_PUNCTUATION             -> "END_PUNCTUATION";
            case FINAL_QUOTE_PUNCTUATION     -> "FINAL_QUOTE_PUNCTUATION";
            case FORMAT                      -> "FORMAT";
            case INITIAL_QUOTE_PUNCTUATION   -> "INITIAL_QUOTE_PUNCTUATION";
            case LETTER_NUMBER               -> "LETTER_NUMBER";
            case LINE_SEPARATOR              -> "LINE_SEPARATOR";
            case LOWERCASE_LETTER            -> "LOWERCASE_LETTER";
            case MATH_SYMBOL                 -> "MATH_SYMBOL";
            case MODIFIER_LETTER             -> "MODIFIER_LETTER";
            case MODIFIER_SYMBOL             -> "MODIFIER_SYMBOL";
            case NON_SPACING_MARK            -> "NON_SPACING_MARK";
            case OTHER_LETTER                -> "OTHER_LETTER";
            case OTHER_NUMBER                -> "OTHER_NUMBER";
            case OTHER_PUNCTUATION           -> "OTHER_PUNCTUATION";
            case OTHER_SYMBOL                -> "OTHER_SYMBOL";
            case PARAGRAPH_SEPARATOR         -> "PARAGRAPH_SEPARATOR";
            case PRIVATE_USE                 -> "PRIVATE_USE";
            case SPACE_SEPARATOR             -> "SPACE_SEPARATOR";
            case START_PUNCTUATION           -> "START_PUNCTUATION";
            case SURROGATE                   -> "SURROGATE";
            case TITLECASE_LETTER            -> "TITLECASE_LETTER";
            case UNASSIGNED                  -> "UNASSIGNED";
            case UPPERCASE_LETTER            -> "UPPERCASE_LETTER";
            default -> "unknown";
        };
    }

    static String toNFD(String input) {
        return Normalizer.normalize(input, Normalizer.Form.NFD);
    }

    private final static Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    /** drop diacritical marks */
    static String drop(String input) {
        return pattern.matcher(toNFD(input)).replaceAll("");
    }

    static String lower(String input) {
        return input.toLowerCase(Locale.ROOT);
    }

    static String genLetters() {
        StringBuilder s = new StringBuilder();
        for (int codepoint = 0; codepoint < 0x110000; codepoint++) {
            if (Character.isLetter(codepoint)) {
                s.appendCodePoint(codepoint);
                s.append('\n');
            }
        }
        return s.toString();
    }

    static String genClasses() {
        int maxType = 0;
        StringBuilder s = new StringBuilder();
        int types[] = new int[32];
        for (int codepoint = 1; codepoint < 0x110000; codepoint++) {
            int type = Character.getType(codepoint);
            types[type]++;
            if (type == LETTER_NUMBER) {
                s.append(typeName(type)).append(" >>> ");
                s.appendCodePoint(0x200E);
                s.appendCodePoint(codepoint);
                s.appendCodePoint(0x200E);
                s.append(" <<< [0x");
                s.append(Integer.toHexString(codepoint).toUpperCase());
                s.append("]\n");
            }
        }
        for (int i = 0; i < 32; i++) {
            String name = typeName(i);
            if (types[i] != 0) {
                System.err.println("Found " + types[i] + " codepoints with type: " + name + " [" + i + "]");
            }
        }
        return s.toString();
    }

    static String genDefineds() {
        StringBuilder s = new StringBuilder();
        boolean defined = Character.isDefined(0);
        int first = 0;
        for (int codepoint = 1; codepoint < 0x110000; codepoint++) {
            if (Character.isDefined(codepoint) != defined) {
                int last = codepoint - 1;
                if (defined) {
                    defined = false;
                    s.append("valid: 0x");
                } else {
                    defined = true;
                    s.append("undef: 0x");
                }
                s.append(Integer.toHexString(first).toUpperCase());
                s.append(" to 0x");
                s.append(Integer.toHexString(last).toUpperCase());
                s.append(" size ");
                s.append(codepoint - first);
                s.append('\n');
                first = codepoint;
            }
        }
        return s.toString();
    }

    public static void main(String[] args) throws Exception {
        String input = genLetters();
        String mode = "";
        if (args.length > 0) mode = args[0];
        String output = switch (mode) {
            case "letters", "" -> input;
            case "lower" -> lower(input);
            case "drop" -> drop(input);
            case "lower-drop" -> drop(lower(input));
            case "drop-lower" -> lower(drop(input));
            case "defined" -> genDefineds();
            case "classes" -> genClasses();
            default -> "Unknown mode: " + mode;
        };
        System.out.write(output.getBytes(StandardCharsets.UTF_8));
    }

}
