// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_escape.h"

#include <vespa/vespalib/stllike/asciistream.h>

#include <format>
#include <ostream>
#include <vector>

namespace vespalib {

namespace {

std::vector<bool> precompute_escaped_xml_chars() {
    std::vector<bool> vec(256, false);
    for (uint32_t i = 0; i < 32; ++i) {
        vec[i] = true;
    }
    vec['\n'] = false;
    vec['<'] = true;
    vec['>'] = true;
    vec['&'] = true;
    return vec;
}

std::vector<bool> escaped_xml_chars = precompute_escaped_xml_chars();

template <typename StreamT> void do_write_xml_content_escaped(StreamT& out, std::string_view str) {
    for (const char s : str) {
        if (escaped_xml_chars[static_cast<uint8_t>(s)]) {
            if (s == '<') {
                out << "&lt;";
            } else if (s == '>') {
                out << "&gt;";
            } else if (s == '&') {
                out << "&amp;";
            } else {
                out << "&#" << static_cast<int>(s) << ";";
            }
        } else {
            out << s;
        }
    }
}

// Note: quotes and backslash are all considered printable chars and must be checked for explicitly
[[nodiscard]] constexpr bool is_printable_char(const char c) noexcept {
    // The [33, 47] block covers !"#$%&'()*+,-./
    // The [58, 64] block covers :;<=>?@
    // The [91, 96] block covers [\]^_`
    // The [123, 126] block covers {|}~
    // Several of these ranges are adjacent and can be merged, but we expect the compiler
    // to do this optimization for us anyway.
    return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || (c >= 33 && c <= 47) ||
           (c >= 58 && c <= 64) || (c >= 91 && c <= 96) || (c >= 123 && c <= 126);
}

} // namespace

std::string xml_attribute_escaped(std::string_view str) {
    vespalib::asciistream ost;
    for (const char s : str) {
        if (s == '"' || s == '\'' || s == '\n' || escaped_xml_chars[static_cast<uint8_t>(s)]) {
            if (s == '<') {
                ost << "&lt;";
            } else if (s == '>') {
                ost << "&gt;";
            } else if (s == '&') {
                ost << "&amp;";
            } else if (s == '"') {
                ost << "&quot;";
            } else if (s == '\'') {
                ost << "&#39;";
            } else {
                ost << "&#" << static_cast<int>(s) << ";";
            }
        } else {
            ost << s;
        }
    }
    return ost.str();
}

std::string xml_content_escaped(std::string_view str) {
    vespalib::asciistream out;
    do_write_xml_content_escaped(out, str);
    return out.str();
}

void write_xml_content_escaped(vespalib::asciistream& out, std::string_view str) {
    do_write_xml_content_escaped(out, str);
}

void write_xml_content_escaped(std::ostream& out, std::string_view str) {
    do_write_xml_content_escaped(out, str);
}

std::string escape(std::string_view s) {
    std::string ret;
    ret.reserve(s.size());
    for (const char c : s) {
        if (is_printable_char(c)) {
            if (c == '\"' || c == '\\') {
                ret += '\\';
            }
            ret += c;
        } else {
            ret += '\\';
            if (c == 0) {
                ret += '0';
            } else if (c >= 7 && c <= 13) {
                constexpr char c_esc_chars[] = "abtnvfr"; // bell, backspace, htab, line feed, vtab, form feed
                ret += c_esc_chars[c - 7];
            } else {
                std::format_to(std::back_inserter(ret), "x{:02x}", c);
            }
        }
    }
    return ret;
}

} // namespace vespalib
