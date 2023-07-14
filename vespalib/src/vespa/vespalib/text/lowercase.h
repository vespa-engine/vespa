// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// $Id$


#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vector>

namespace vespalib {

/**
 * @brief Utility class for converting UCS-4 codepoints to lowercase
 **/
class LowerCase
{
private:
    static unsigned char lowercase_0_block[256];
    static uint32_t lowercase_1_block[256];
    static uint32_t lowercase_2_block[256];
    static uint32_t lowercase_3_block[256];
    static uint32_t lowercase_4_block[256];
    static uint32_t lowercase_5_block[256];
    static uint32_t lowercase_16_block[256];
    static uint32_t lowercase_30_block[256];
    static uint32_t lowercase_31_block[256];
    static uint32_t lowercase_33_block[256];
    static uint32_t lowercase_44_block[256];
    static uint32_t lowercase_260_block[256];
    static uint32_t lowercase_0_5_blocks[0x600];

public:
    /**
     * lowercase a single UCS-4 character.
     *
     * Note that this functions expects NFKC normalized input,
     * so if a character does not transform to itself under NFKC
     * there will be no match in the lowercasing tables.
     * For all input not found in the tables, the input is returned
     * unchanged as output; this includes both genuine lowercase
     * characters and anything where lowercasing is meaningless.
     *
     * @param codepoint the character codepoint to be lowercased.
     * @return lowercase UCS-4 character (codepoint if no lowercasing is performed).
     **/
    static uint32_t convert(uint32_t codepoint)
    {
        if (codepoint < 0x100) {
            return lowercase_0_block[codepoint];
        } else if (codepoint < 0x600) {
            return lowercase_0_5_blocks[codepoint];
        } else {
            int lowblock = codepoint >> 8;
            unsigned char lb = codepoint & 0xFF;

            /**/ if (lowblock == 16) {
                return lowercase_16_block[lb];
            }
            else if (lowblock == 30) {
                return lowercase_30_block[lb];
            }
            else if (lowblock == 31) {
                return lowercase_31_block[lb];
            }
            else if (lowblock == 33) {
                return lowercase_33_block[lb];
            }
            else if (lowblock == 44) {
                return lowercase_44_block[lb];
            }
            else if (lowblock == 260) {
                return lowercase_260_block[lb];
            }
            else {
                return codepoint;
            }
        }
    }

    /**
     * lowercase a single ASCII (or iso 8859-1) character
     **/
    static unsigned char convert(unsigned char c) {
        return convert((uint32_t)c);
    }

    /**
     * lowercase a single ASCII (or iso 8859-1) character
     **/
    static char convert(char c) {
        return convert((unsigned char)c);
    }

    /**
     * lowercase a single ASCII (or iso 8859-1) character
     **/
    static signed char convert(signed char c) {
        return convert((unsigned char)c);
    }

    /**
     * lowercase a string in UTF-8 format; note that this will replace
     * any bytes that aren't valid UTF-8 with the Unicode REPLACEMENT
     * CHARACTER (U+FFFD).
     **/
    static vespalib::string convert(vespalib::stringref input);

    /**
     * Lowercase a string in UTF-8 format while converting it to UCS-4 codepoints.
     */
    static std::vector<uint32_t> convert_to_ucs4(vespalib::stringref input);
    static std::vector<uint32_t> convert_to_ucs4(std::string_view input) {
        return convert_to_ucs4(vespalib::stringref(input.data(), input.size()));
    }

};


}  // namespace vespalib

