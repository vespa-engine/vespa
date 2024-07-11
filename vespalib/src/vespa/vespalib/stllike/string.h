// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "small_string.h"
#include <cstring>
#include <string_view>
#include <string>

#define VESPA_DLL_LOCAL  __attribute__ ((visibility("hidden")))

namespace vespalib {

using string = small_string<48>;

inline bool contains(std::string_view text, std::string_view key) noexcept {
    return text.find(key) != std::string_view::npos;
}

inline bool starts_with(std::string_view text, std::string_view key) noexcept {
    if (text.size() >= key.size()) {
        return memcmp(text.begin(), key.begin(), key.size()) == 0;
    }
    return false;
}

inline bool ends_with(std::string_view text, std::string_view key) noexcept {
    if (text.size() >= key.size()) {
        return memcmp(text.end()-key.size(), key.begin(), key.size()) == 0;
    }
    return false;
}

// returns a reference to a shared empty string
const string &empty_string() noexcept;

/**
 * Utility function to format an unsigned integer into a new
 * string instance.
 **/
static inline string stringify(uint64_t number) noexcept
{
    char digits[64];
    int numdigits = 0;
    do {
        digits[numdigits++] = '0' + (number % 10);
        number /= 10;
    } while (number > 0);
    string retval;
    while (numdigits > 0) {
        retval.append(digits[--numdigits]);
    }
    return retval;
}

void chomp(vespalib::string & s) noexcept;
vespalib::string safe_char_2_string(const char * p);

} // namespace vespalib

namespace std {

vespalib::string operator+(std::string_view a, std::string_view b) noexcept;
vespalib::string operator+(const char *a, std::string_view b) noexcept;
vespalib::string operator+(std::string_view a, const char *b) noexcept;

}
