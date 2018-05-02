// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;


import java.util.Map;
import java.util.HashMap;


/**
 * Static HTML escaping stuff
 *
 * @author  <a href="mailto:borud@yahoo-inc.com">Bjorn Borud</a>
 */
public class HTML {
    static Object[][] entities = {
        // {"#39", new Integer(39)},     // ' - apostrophe
        {"quot", 34},    // " - double-quote
        {"amp", 38},     // & - ampersand
        {"lt", 60},      // < - less-than
        {"gt", 62},      // > - greater-than
        {"nbsp", 160},   // non-breaking space
        {"copy", 169},   // \u00A9 - copyright
        {"reg", 174},    // \u00AE - registered trademark
        {"Agrave", 192}, // \u00C0 - uppercase A, grave accent
        {"Aacute", 193}, // \u00C1 - uppercase A, acute accent
        {"Acirc", 194},  // \u00C2 - uppercase A, circumflex accent
        {"Atilde", 195}, // \u00C3 - uppercase A, tilde
        {"Auml", 196},   // \u00C4 - uppercase A, umlaut
        {"Aring", 197},  // \u00C5 - uppercase A, ring
        {"AElig", 198},  // \u00C6 - uppercase AE
        {"Ccedil", 199}, // \u00C7 - uppercase C, cedilla
        {"Egrave", 200}, // \u00C8 - uppercase E, grave accent
        {"Eacute", 201}, // \u00C9 - uppercase E, acute accent
        {"Ecirc", 202},  // \u00CA - uppercase E, circumflex accent
        {"Euml", 203},   // \u00CB - uppercase E, umlaut
        {"Igrave", 204}, // \u00CC - uppercase I, grave accent
        {"Iacute", 205}, // \u00CD - uppercase I, acute accent
        {"Icirc", 206},  // \u00CE - uppercase I, circumflex accent
        {"Iuml", 207},   // \u00CF - uppercase I, umlaut
        {"ETH", 208},    // \u00D0 - uppercase Eth, Icelandic
        {"Ntilde", 209}, // \u00D1 - uppercase N, tilde
        {"Ograve", 210}, // \u00D2 - uppercase O, grave accent
        {"Oacute", 211}, // \u00D3 - uppercase O, acute accent
        {"Ocirc", 212},  // \u00D4 - uppercase O, circumflex accent
        {"Otilde", 213}, // \u00D5 - uppercase O, tilde
        {"Ouml", 214},   // \u00D6 - uppercase O, umlaut
        {"Oslash", 216}, // \u00D8 - uppercase O, slash
        {"Ugrave", 217}, // \u00D9 - uppercase U, grave accent
        {"Uacute", 218}, // \u00DA - uppercase U, acute accent
        {"Ucirc", 219},  // \u00DB - uppercase U, circumflex accent
        {"Uuml", 220},   // \u00DC - uppercase U, umlaut
        {"Yacute", 221}, // \u00DD - uppercase Y, acute accent
        {"THORN", 222},  // \u00DE - uppercase THORN, Icelandic
        {"szlig", 223},  // \u00DF - lowercase sharps, German
        {"agrave", 224}, // \u00E0 - lowercase a, grave accent
        {"aacute", 225}, // \u00E1 - lowercase a, acute accent
        {"acirc", 226},  // \u00E2 - lowercase a, circumflex accent
        {"atilde", 227}, // \u00E3 - lowercase a, tilde
        {"auml", 228},   // \u00E4 - lowercase a, umlaut
        {"aring", 229},  // \u00E5 - lowercase a, ring
        {"aelig", 230},  // \u00E6 - lowercase ae
        {"ccedil", 231}, // \u00E7 - lowercase c, cedilla
        {"egrave", 232}, // \u00E8 - lowercase e, grave accent
        {"eacute", 233}, // \u00E9 - lowercase e, acute accent
        {"ecirc", 234},  // \u00EA - lowercase e, circumflex accent
        {"euml", 235},   // \u00EB - lowercase e, umlaut
        {"igrave", 236}, // \u00EC - lowercase i, grave accent
        {"iacute", 237}, // \u00ED - lowercase i, acute accent
        {"icirc", 238},  // \u00EE - lowercase i, circumflex accent
        {"iuml", 239},   // \u00EF - lowercase i, umlaut
        {"igrave", 236}, // \u00EC - lowercase i, grave accent
        {"iacute", 237}, // \u00ED - lowercase i, acute accent
        {"icirc", 238},  // \u00EE - lowercase i, circumflex accent
        {"iuml", 239},   // \u00EF - lowercase i, umlaut
        {"eth", 240},    // \u00F0 - lowercase eth, Icelandic
        {"ntilde", 241}, // \u00F1 - lowercase n, tilde
        {"ograve", 242}, // \u00F2 - lowercase o, grave accent
        {"oacute", 243}, // \u00F3 - lowercase o, acute accent
        {"ocirc", 244},  // \u00F4 - lowercase o, circumflex accent
        {"otilde", 245}, // \u00F5 - lowercase o, tilde
        {"ouml", 246},   // \u00F6 - lowercase o, umlaut
        {"oslash", 248}, // \u00F8 - lowercase o, slash
        {"ugrave", 249}, // \u00F9 - lowercase u, grave accent
        {"uacute", 250}, // \u00FA - lowercase u, acute accent
        {"ucirc", 251},  // \u00FB - lowercase u, circumflex accent
        {"uuml", 252},   // \u00FC - lowercase u, umlaut
        {"yacute", 253}, // \u00FD - lowercase y, acute accent
        {"thorn", 254},  // \u00FE - lowercase thorn, Icelandic
        {"yuml", 255},   // \u00FF - lowercase y, umlaut
        {"euro", 8364},  // Euro symbol
    };

    static Map<String, Integer> e2i = new HashMap<>();
    static Map<Integer, String> i2e = new HashMap<>();

    static {
        for (Object[] entity : entities) {
            e2i.put((String) entity[0], (Integer) entity[1]);
            i2e.put((Integer) entity[1], (String) entity[0]);
        }
    }

    public static String htmlescape(String s1) {
        if (s1 == null) return "";

        int len = s1.length();
        // about 20% guess
        StringBuilder buf = new StringBuilder((int) (len * 1.2));
        int i;

        for (i = 0; i < len; ++i) {
            char ch = s1.charAt(i);
            String entity = i2e.get((int) ch);

            if (entity == null) {
                if (((int) ch) > 128) buf.append("&#").append((int) ch).append(";");
                else buf.append(ch);
            } else {
                buf.append("&").append(entity).append(";");
            }
        }
        return buf.toString();
    }
}
