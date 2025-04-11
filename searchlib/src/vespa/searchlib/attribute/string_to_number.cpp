// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_to_number.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/exceptions.h>

namespace search {

template <typename T>
T
string_to_number(std::string_view str)
{
    if (str.empty()) {
        return T();
    }
    vespalib::asciistream iss(str);
    T result;
    try {
        iss >> result;
    } catch (const std::exception&) {
        throw vespalib::IllegalArgumentException("Failed converting string '" + std::string(str) + "' to a number");
    }
    return result;
}

template <>
int8_t
string_to_number<int8_t>(std::string_view str) {
    return string_to_number<int16_t>(str);
}

template int16_t string_to_number<int16_t>(std::string_view str);
template int32_t string_to_number<int32_t>(std::string_view str);
template int64_t string_to_number<int64_t>(std::string_view str);
template float string_to_number<float>(std::string_view str);
template double string_to_number<double>(std::string_view str);

}
