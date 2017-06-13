// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/documentapi/common.h>

namespace documentapi {

/**
 * <p>Utility class for HTML form encoding. This class contains static methods for converting a String to the
 * application/x-www-form-urlencoded MIME format. For more information about HTML form encoding, consult the
 * HTML specification.</p>
 *
 * <p>When encoding a String, the following rules apply:</p>
 * <ul>
 *     <li>The alphanumeric characters "a" through "z", "A" through "Z" and "0" through "9" remain the
 *     same.</li>
 *     <li>The special characters ".", "-", "*", and "_" remain the same.</li>
 *     <li>The space character " " is converted into a plus sign "+".</li>
 *     <li>All other characters are unsafe and are first converted into one or more bytes using some encoding
 *     scheme. Then each byte is represented by the 3-character string "%xy", where xy is the two-digit
 *     hexadecimal representation of the byte. The recommended encoding scheme to use is UTF-8. However, for
 *     compatibility reasons, if an encoding is not specified, then the default encoding of the platform is
 *     used.</li>
 * </ul>
 *
 * <p>For example using UTF-8 as the encoding scheme the string "The string �@foo-bar" would get converted to
 * "The+string+%C3%BC%40foo-bar" because in UTF-8 the character � is encoded as two bytes C3 (hex) and BC
 * (hex), and the character @ is encoded as one byte 40 (hex).</p>
 */
class URLEncoder {
public:
    /**
     * Translates a string into application/x-www-form-urlencoded format using a UTF-8 encoding.
     *
     * @param str The string to be translated.
     * @return The translated string.
     */
    static const string encode(const string &str);
};

}

