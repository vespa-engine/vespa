// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// $Id$


#pragma once

#include <cstdint>
#include <string>
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
    static uint32_t lowercase_19_block[256];
    static uint32_t lowercase_28_block[256];
    static uint32_t lowercase_30_block[256];
    static uint32_t lowercase_31_block[256];
    static uint32_t lowercase_33_block[256];
    static uint32_t lowercase_44_block[256];
    static uint32_t lowercase_166_block[256];
    static uint32_t lowercase_167_block[256];
    static uint32_t lowercase_255_block[256];
    static uint32_t lowercase_260_block[256];
    static uint32_t lowercase_268_block[256];
    static uint32_t lowercase_280_block[256];
    static uint32_t lowercase_366_block[256];
    static uint32_t lowercase_489_block[256];
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
    static uint32_t convert(uint32_t codepoint) noexcept
    {
        if (codepoint < 0x100) [[likely]] {
            return lowercase_0_block[codepoint];
        } else if (codepoint < 0x600) {
            return lowercase_0_5_blocks[codepoint];
        } else {
            int lowblock = codepoint >> 8;
            unsigned char lb = codepoint & 0xFF;

            switch (lowblock) {
            case 16: return lowercase_16_block[lb];
            case 19: return lowercase_19_block[lb];
            case 28: return lowercase_28_block[lb];
            case 30: return lowercase_30_block[lb];
            case 31: return lowercase_31_block[lb];
            case 33: return lowercase_33_block[lb];
            case 44: return lowercase_44_block[lb];
            case 166: return lowercase_166_block[lb];
            case 167: return lowercase_167_block[lb];
            case 255: return lowercase_255_block[lb];
            case 260: return lowercase_260_block[lb];
            case 268: return lowercase_268_block[lb];
            case 280: return lowercase_280_block[lb];
            case 366: return lowercase_366_block[lb];
            case 489: return lowercase_489_block[lb];
            default:
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
    static std::string convert(std::string_view input);

    /**
     * Lowercase a string in UTF-8 format while converting it to UCS-4 codepoints.
     */
    static std::vector<uint32_t> convert_to_ucs4(std::string_view input);

};


}  // namespace vespalib

