// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_spec.h"
#include <vespa/vespalib/stllike/string.h>

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
    bool maybe_add_comma(vespalib::string &dst) {
        if (first) {
            first = false;
            return false;
        } else {
            dst.push_back(',');
            return true;
        }
    }
    template <typename T>
    bool maybe_eat_comma(T &ctx) {
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
vespalib::string as_quoted_string(const vespalib::string &str);

/**
 * Is this string a positive integer (dimension index)
 **/
bool is_number(const vespalib::string &str);

/**
 * Convert this string to a positive integer (dimension index)
 **/
size_t as_number(const vespalib::string &str);

/**
 * Convert a tensor spec address into a string on the form:
 * '{dim1:label,dim2:index, ...}'
 **/
vespalib::string as_string(const TensorSpec::Address &address);

}
