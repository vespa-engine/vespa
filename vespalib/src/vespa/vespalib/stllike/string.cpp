// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string.h"
#include <algorithm>
#include <cctype>
#include <istream>
#include <ostream>

namespace vespalib {

const std::string &empty_string() noexcept {
    static std::string empty;
    return empty;
}

inline namespace waiting_for_godot {

void ltrim(std::string &s) noexcept;
void rtrim(std::string &s) noexcept;

void
ltrim(std::string &s) noexcept {
    s.erase(s.begin(), std::find_if(s.begin(), s.end(), [](unsigned char c) {
        return !std::isspace(c);
    }));
}

void
rtrim(std::string &s) noexcept {
    s.erase(std::find_if(s.rbegin(), s.rend(), [](unsigned char c) {
        return !std::isspace(c);
    }).base(), s.end());
}
}

void
chomp(std::string & s) noexcept {
    ltrim(s);
    rtrim(s);
}

std::string
safe_char_2_string(const char * p) {
    return (p != nullptr) ? std::string(p) : std::string("");
}

}
