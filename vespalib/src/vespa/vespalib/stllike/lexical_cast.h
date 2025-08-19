// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/exceptions.h>

namespace vespalib {

template <typename T>
T lexical_cast(const std::string_view s)
{
    const char *fp = s.data();
    const char *lp = fp + s.size();
    T val;
    if constexpr (std::is_integral_v<T>) {
        auto res = std::from_chars(fp, lp, val);
        if (res.ec == std::errc{} && res.ptr == lp) [[likely]] {
            return val;
        }
    } else {
        asciistream is(s);
        is >> val;
        if (is.empty()) [[likely]] {
            return val;
        }
    }
    throw IllegalArgumentException("Failed decoding number from string: " + std::string(s));
}

}
