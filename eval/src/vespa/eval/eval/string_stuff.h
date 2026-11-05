// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>
#include <string>

namespace vespalib::eval {

/**
 * Helper class used to insert commas on the appropriate places in
 * comma-separated textual lists. Can also be used to figure out when
 * to expect commas when parsing text.
 **/
struct CommaTracker {
    bool first;
    CommaTracker() noexcept : first(true) {}
    CommaTracker(bool first_in) noexcept : first(first_in) {}
    bool maybe_add_comma(std::string& dst) {
        if (first) {
            first = false;
            return false;
        } else {
            dst.push_back(',');
            return true;
        }
    }
    template <typename T> bool maybe_eat_comma(T& ctx) {
        if (first) {
            first = false;
            return false;
        } else {
            ctx.eat(',');
            return true;
        }
    }
};

/**
 * Convert the given string to a quoted string with escaped special characters.
 **/
std::string as_quoted_string(const std::string& str);

/**
 * Is this string a non-negative decimal integer (only digits, at
 * least one). Note that this does not check whether the value fits
 * in size_t; use as_number for that.
 **/
bool is_number(const std::string& str);

/**
 * Convert this string to a non-negative integer (e.g. a dimension
 * index or an indexed dimension size). Strings that are not numbers
 * (see is_number) or that do not fit in size_t result in SIZE_MAX,
 * which callers should treat as out of range (it fails any bounds
 * check against a dimension size, and is >= Dimension::npos).
 **/
size_t as_number(const std::string& str);

} // namespace vespalib::eval
