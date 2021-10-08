// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "parse_utils.h"
#include <boost/spirit/include/qi.hpp>

namespace document::select::util {

namespace qi = boost::spirit::qi;

// TODO replace use of Spirit.Qi with std::from_string when available.
// Note: these parsers are all pure, reentrant and without locking.
bool parse_hex_i64(const char* str, size_t len, int64_t& out) {
    const char* iter = str;
    const char* end = str + len;
    // Legacy parser parses hex numbers as u64 rather than i64 (then implicitly
    // converts), so we do the same thing here to avoid change of semantics.
    using u64_hex_parser = qi::uint_parser<uint64_t, 16, 1, 16>;
    u64_hex_parser u64_hex;
    uint64_t tmp = 0;
    const bool ok = qi::parse(iter, end, u64_hex, tmp);
    out = static_cast<int64_t>(tmp);
    return (ok && (iter == end));
}
bool parse_i64(const char* str, size_t len, int64_t& out) {
    const char* iter = str;
    const char* end = str + len;
    const bool ok = qi::parse(iter, end, qi::long_long, out);
    return (ok && (iter == end));
}
bool parse_double(const char* str, size_t len, double& out) {
    const char* iter = str;
    const char* end = str + len;
    const bool ok = qi::parse(iter, end, qi::double_, out);
    return (ok && (iter == end));
}

}
