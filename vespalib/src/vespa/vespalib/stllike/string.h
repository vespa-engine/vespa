// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/vespa_dll_local.h>
#include <cstdint>
#include <cstring>
#include <string_view>
#include <string>

namespace vespalib {

inline bool contains(std::string_view text, std::string_view key) noexcept {
    return text.find(key) != std::string_view::npos;
}

// returns a reference to a shared empty string
const std::string &empty_string() noexcept;

/**
 * Utility function to format an unsigned integer into a new
 * string instance.
 **/
static inline std::string stringify(uint64_t number) noexcept
{
    char digits[64];
    int numdigits = 0;
    do {
        digits[numdigits++] = '0' + (number % 10);
        number /= 10;
    } while (number > 0);
    std::string retval;
    while (numdigits > 0) {
        retval += digits[--numdigits];
    }
    return retval;
}

void chomp(std::string & s) noexcept;
std::string safe_char_2_string(const char * p);

} // namespace vespalib
