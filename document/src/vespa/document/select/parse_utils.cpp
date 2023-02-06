// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "parse_utils.h"
#include <charconv>
#include <limits>

namespace document::select::util {

// Note: these parsers are all pure, reentrant and without locking.
bool
parse_hex_i64(const char* str, size_t len, int64_t& out) {
    // Legacy parser parses hex numbers as u64 rather than i64 (then implicitly
    // converts), so we do the same thing here to avoid change of semantics.
    uint64_t val = out;
    auto res = std::from_chars(str, str+len, val, 16);
    out = val;
    return (res.ec == std::errc()) && (res.ptr == str+len);
}
bool
parse_i64(const char* str, size_t len, int64_t& out) {
    auto res = std::from_chars(str, str+len, out, 10);
    return (res.ec == std::errc()) && (res.ptr == str+len);
}
bool
parse_double(const char* str, size_t len, double& out) {
    auto res = std::from_chars(str, str+len, out);
    if (res.ec == std::errc::result_out_of_range) {
        out = (str[0] == '-')
            ? -std::numeric_limits<double>::infinity()
            : std::numeric_limits<double>::infinity();
        return true;
    }
    return (res.ec == std::errc()) && (res.ptr == str+len);
}

}
