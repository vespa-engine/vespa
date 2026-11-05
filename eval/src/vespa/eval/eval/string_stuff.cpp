// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_stuff.h"

#include <cctype>
#include <charconv>
#include <limits>

namespace vespalib::eval {

std::string as_quoted_string(const std::string& str) {
    std::string res;
    res.push_back('"');
    for (char c : str) {
        switch (c) {
        case '\\':
            res.append("\\\\");
            break;
        case '"':
            res.append("\\\"");
            break;
        case '\t':
            res.append("\\t");
            break;
        case '\n':
            res.append("\\n");
            break;
        case '\r':
            res.append("\\r");
            break;
        case '\f':
            res.append("\\f");
            break;
        default:
            if (static_cast<unsigned char>(c) >= 32 && static_cast<unsigned char>(c) <= 126) {
                res.push_back(c);
            } else {
                const char* lookup = "0123456789abcdef";
                res.append("\\x");
                res.push_back(lookup[(c >> 4) & 0xf]);
                res.push_back(lookup[c & 0xf]);
            }
        }
    }
    res.push_back('"');
    return res;
}

bool is_number(const std::string& str) {
    if (str.empty()) {
        return false;
    }
    for (char c : str) {
        if (!std::isdigit(static_cast<unsigned char>(c))) {
            return false;
        }
    }
    return true;
}

size_t as_number(const std::string& str) {
    size_t      value = 0;
    const char* end = str.data() + str.size();
    auto [ptr, ec] = std::from_chars(str.data(), end, value);
    if ((ec != std::errc{}) || (ptr != end)) {
        return std::numeric_limits<size_t>::max();
    }
    return value;
}

} // namespace vespalib::eval
