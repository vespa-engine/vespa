// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.parser;

import java.io.PrintStream;


/**
 * Dump properties of unicode characters in a format compatible
 * with fastlib/text/unicode_propertydump
 *
 * <p>Arguments:</p>
 *
 * <ol>
 *  <li>start-char-number</li>
 *  <li>end-char-number</li>
 *  <li>debug true|false</li>
 * </ol>
 *
 * @author <a href="mailto:vlarsen@yahoo-inc.com">Vidar Larsen</a>
 */
class UnicodePropertyDump {
    public static void main(String[] arg) {
        int start = 0;
        int end = 0xffff;
        boolean debug = false;

        if (arg.length > 0) {
            start = Integer.valueOf(arg[0]).intValue();
        }
        if (arg.length > 1) {
            end = Integer.valueOf(arg[1]).intValue();
        }
        if (arg.length > 2) {
            debug = new Boolean(arg[2]).booleanValue();
        }
        dumpProperties(start, end, debug, System.out);
    }

    static void dumpProperties(int start, int end, boolean debug, PrintStream out) {
        for (int i = start; i < end; i++) {
            // printf("%08x ", i);
            String charcode = Integer.toHexString(i);

            while (charcode.length() < 8) {
                charcode = "0" + charcode;
            }
            out.print(charcode + " ");

            /*
             * compute property bitmap fastlib-style
             * bit 0 = white space
             * bit 1 = word char
             * bit 2 = ideographic
             * bit 3 = decimal digit
             * bit 4 = ignorable control
             *
             * White_Space = 0x01
             * Alphabetic = 0x02
             * Diacritic = 0x02
             * Extender = 0x02
             * Custom_word_char = 0x02
             * Ideographic = 0x04
             * Nd = 0x0A  (both digit and alphabetic)
             * Default_Ignorable_Code_Point = 0x10
             * Custom_Non_Word_Char = ~0x02
             *
             * Uses both PropList, DerivedCoreProperties, CustomProperties
             * and UnicodeData
             */
            int map = 0;
            char the_char = (char) i;
            int char_type = Character.getType(the_char);

            if (Character.isWhitespace(the_char)) {
                map |= 0x01;
            }

            if (Character.isLetter(the_char)) {
                map |= 0x02;
            }

            if (Character.getType(the_char) == Character.OTHER_LETTER) {
                map |= 0x04;
            }

            if (Character.isDigit(the_char)) {
                map |= 0x0A;
            }

            if ((char_type == Character.CONTROL || char_type == Character.FORMAT
                    || char_type == Character.SURROGATE
                    || char_type == Character.UNASSIGNED)
                    && !Character.isWhitespace(the_char)
                    ) {
                map |= 0x10;
            }

            // printf("%04x\n", map);
            String mapcode = Integer.toHexString(map);

            while (mapcode.length() < 4) {
                mapcode = "0" + mapcode;
            }
            out.print(mapcode);
            if (debug) {
                out.print(" " + char_type);
            }
            out.println();
        }
    }
}
