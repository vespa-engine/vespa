// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>
#include <string>
#include <string_view>
#include <vector>

namespace vespalib::fuzzy {

std::vector<uint32_t> utf8_string_to_utf32(std::string_view str);

std::vector<uint32_t> utf8_string_to_utf32(std::u8string_view u8str);

/**
 * Encodes a single UTF-32 codepoint `u32_char` to a 1-4 byte UTF-8 sequence and
 * appends it to `out_str.`
 *
 * Note that this will happily encode code points that aren't technically part of
 * the valid UTF-8 range, but which will still be correct in memcmp() byte-wise
 * ordering, which is the API contract we expose.
 *
 * In particular, this includes:
 *   - high/low surrogate ranges U+D800 through U+DFFF (surrogate pairs not allowed
 *     in UTF-8)
 *   - U+10FFFF + 1 (outside max code point range by one)
 *
 * ... So don't copy this function for use as a general UTF-8 emitter, as it is not
 * _technically_ conformant!
 */
void append_utf32_char_as_utf8(std::string& out_str, uint32_t u32_char);

}
