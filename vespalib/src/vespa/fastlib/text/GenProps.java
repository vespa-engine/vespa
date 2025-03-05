// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import static java.lang.Character.*;

import java.nio.charset.StandardCharsets;

// program to generate code tables used by Fast_UnicodeUtil::IsWordChar()

public class GenProps {

    static boolean isWordChar(int codepoint) {
        if (Character.isLetterOrDigit(codepoint)) return true;
        int type = Character.getType(codepoint);
        switch (type) {
            case CONTROL                     : return false;
            case SURROGATE                   : return false;
            case UNASSIGNED                  : return false;
            case PRIVATE_USE                 : return false;

            case LOWERCASE_LETTER            : return true;
            case TITLECASE_LETTER            : return true;
            case UPPERCASE_LETTER            : return true;
            case OTHER_LETTER                : return true;
            case DECIMAL_DIGIT_NUMBER        : return true;

            case LETTER_NUMBER               : return true;
            case OTHER_NUMBER                : return false;

            case DASH_PUNCTUATION            : return false;
            case START_PUNCTUATION           : return false;
            case END_PUNCTUATION             : return false;
            case FINAL_QUOTE_PUNCTUATION     : return false;
            case INITIAL_QUOTE_PUNCTUATION   : return false;
            case OTHER_PUNCTUATION           : return false;
            case CONNECTOR_PUNCTUATION       : return false; // '_'

            case MATH_SYMBOL                 : return false;
            case CURRENCY_SYMBOL             : return false;
            case OTHER_SYMBOL                : return false;

            case COMBINING_SPACING_MARK:
                // example: 093E;DEVANAGARI VOWEL SIGN AA
                return true;

            case NON_SPACING_MARK:
                // example: 0300;COMBINING GRAVE ACCENT
                return true;

            case ENCLOSING_MARK:
                // example: 20DD;COMBINING ENCLOSING CIRCLE
                return false;

            case FORMAT                      : return false;
            case LINE_SEPARATOR              : return false;
            case SPACE_SEPARATOR             : return false;
            case PARAGRAPH_SEPARATOR         : return false;

            case MODIFIER_LETTER             : return true;
            case MODIFIER_SYMBOL             : return false;
        }
        return false;
    }

    static String genTable() {
        StringBuilder s = new StringBuilder();
        s.append("unsigned long Fast_UnicodeUtil::_wordCharBits[3216] = {\n");
        for (int codepoint = 0; codepoint < 804 * 0x100; ) {
            int nextpos = s.length() + 4;
            for (int w = 0; w < 4; w++) {
                long val = 0;
                for (int j = 0; j < 64; j++) {
                    if (isWordChar(codepoint)) {
                        val |= (1L << j);
                    }
                    ++codepoint;
                }
                while (s.length() < nextpos) s.append(' ');
                nextpos += 18;
                s.append("0x");
                String hex = Long.toHexString(val).toUpperCase();
                while (s.length() + hex.length() < nextpos) s.append('0');
                s.append(hex);
                if (codepoint + 1 < 804 * 0x100) s.append(",");
                nextpos += 4;
            }
            s.append("\n");
        }
        s.append("};\n\n");
        return s.toString();
    }

    public static void main(String[] args) throws Exception {
        String output = genTable();
        System.out.write(output.getBytes(StandardCharsets.UTF_8));
    }

}
