// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/basictype.h>
#include <vespa/vespalib/util/typify.h>

#include <cstdint>

namespace search::attribute {

/*
 * Typifier (see vespalib/util/typify.h) resolving an attribute basic type into a type:
 * the C++ value type for numeric basic types (int8_t, int16_t, int32_t, int64_t, float, double),
 * TypifyBasicType::String for strings and TypifyBasicType::ComplexBasicType for anything else.
 * Note that "bool" is considered Complex - it needs special handling when it's handled at all.
 *
 * ComplexBasicType is a regular result rather than an error, so the typify_invoke target can
 * decide how to handle it (typically with if constexpr).
 */
struct TypifyBasicType {
    struct String {};
    struct ComplexBasicType {};
    template <typename T> using Result = vespalib::TypifyResultType<T>;
    template <typename F> static decltype(auto) resolve(BasicType::Type value, F&& f) {
        switch (value) {
        case BasicType::INT8:
            return f(Result<int8_t>());
        case BasicType::INT16:
            return f(Result<int16_t>());
        case BasicType::INT32:
            return f(Result<int32_t>());
        case BasicType::INT64:
            return f(Result<int64_t>());
        case BasicType::FLOAT:
            return f(Result<float>());
        case BasicType::DOUBLE:
            return f(Result<double>());
        case BasicType::STRING:
            return f(Result<String>());
        default:
            return f(Result<ComplexBasicType>());
        }
    }
};

} // namespace search::attribute
