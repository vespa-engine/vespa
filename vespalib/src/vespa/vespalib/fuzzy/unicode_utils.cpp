// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "unicode_utils.h"
#include <vespa/vespalib/text/lowercase.h>
#include <vespa/vespalib/text/utf8.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <stdexcept>

namespace vespalib::fuzzy {

namespace {

template <bool ToLowercase>
std::vector<uint32_t> utf8_string_to_utf32_impl(std::string_view str) {
    stringref ch_str(str.data(), str.size());
    Utf8Reader utf8_reader(ch_str); // TODO consider integrating simdutf library
    std::vector<uint32_t> u32ret;
    u32ret.reserve(str.size()); // Will over-allocate for all non-ASCII
    while (utf8_reader.hasMore()) {
        if constexpr (ToLowercase) {
            u32ret.emplace_back(LowerCase::convert(utf8_reader.getChar()));
        } else {
            u32ret.emplace_back(utf8_reader.getChar());
        }

    }
    return u32ret;
}

}

std::vector<uint32_t> utf8_string_to_utf32_lowercased(std::string_view str) {
    return utf8_string_to_utf32_impl<true>(str);
}

std::vector<uint32_t> utf8_string_to_utf32(std::string_view str) {
    return utf8_string_to_utf32_impl<false>(str);
}

std::vector<uint32_t> utf8_string_to_utf32(std::u8string_view u8str) {
    return utf8_string_to_utf32(std::string_view(reinterpret_cast<const char*>(u8str.data()), u8str.size()));
}

std::string utf32_string_to_utf8(std::span<const uint32_t> u32str) {
    std::string u8out;
    for (uint32_t u32ch : u32str) {
        append_utf32_char(u8out, u32ch);
    }
    return u8out;
}

[[noreturn]] void throw_bad_code_point(uint32_t codepoint) __attribute__((noinline));
[[noreturn]] void throw_bad_code_point(uint32_t codepoint) {
    throw std::invalid_argument(make_string("invalid UTF-32 codepoint: U+%04X (%u)", codepoint, codepoint));
}

namespace {

/**
 * Encodes a single UTF-32 `codepoint` to a 1-4 byte UTF-8 sequence.
 * `
 * `u8buf` must point to a buffer with at least 4 writable bytes.
 *
 * Returns the number of bytes written.
 *
 * See comments on append_utf32_char() as to why this is not a generic UTF-8
 * encoding function that can be used in all possible scenarios.
 */
[[nodiscard]] uint8_t encode_utf8_char(uint32_t codepoint, unsigned char* u8buf) {
    constexpr const uint8_t low_6bits_mask = 0x3F;

    // Yanked and modified from utf8.cpp:
    if (codepoint < 0x80) {
        u8buf[0] = (char) codepoint;
        return 1;
    } else if (codepoint < 0x800) {
        char low6 = (codepoint & low_6bits_mask);
        low6 |= 0x80;
        codepoint >>= 6;
        char first5 = codepoint;
        first5 |= 0xC0;
        u8buf[0] = first5;
        u8buf[1] = low6;
        return 2;
    } else if (codepoint < 0x10000) {
        char low6 = (codepoint & low_6bits_mask);
        low6 |= 0x80;

        codepoint >>= 6;
        char mid6 = (codepoint & low_6bits_mask);
        mid6 |= 0x80;

        codepoint >>= 6;
        char first4 = codepoint;
        first4 |= 0xE0;

        u8buf[0] = first4;
        u8buf[1] = mid6;
        u8buf[2] = low6;
        return 3;
    } else if (codepoint <= 0x110000) { // Explicitly _include_ U+10FFFF + 1!
        char low6 = (codepoint & low_6bits_mask);
        low6 |= 0x80;

        codepoint >>= 6;
        char mid6 = (codepoint & low_6bits_mask);
        mid6 |= 0x80;

        codepoint >>= 6;
        char hi6 = (codepoint & low_6bits_mask);
        hi6 |= 0x80;

        codepoint >>= 6;
        char first3 = codepoint;
        first3 |= 0xF0;

        u8buf[0] = first3;
        u8buf[1] = hi6;
        u8buf[2] = mid6;
        u8buf[3] = low6;
        return 4;
    } else {
        throw_bad_code_point(codepoint);
    }
}

} // anon ns

// TODO optimize inlined in header for case where u32_char is < 0x80?
void append_utf32_char(std::string& out_str, uint32_t u32_char) {
    unsigned char u8buf[4];
    uint8_t u8bytes = encode_utf8_char(u32_char, u8buf);
    out_str.append(reinterpret_cast<const char*>(u8buf), u8bytes);
}

}
