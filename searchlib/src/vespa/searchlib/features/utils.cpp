// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "utils.hpp"
#include <charconv>

namespace search::features::util {

template <typename T>
T strToInt(vespalib::stringref str)
{
    T retval = 0;
    if ((str.size() > 2) && (str[0] == '0') && ((str[1] | 0x20) == 'x')) {
        std::from_chars(str.data()+2, str.data()+str.size(), retval, 16);
    } else {
        std::from_chars(str.data(), str.data()+str.size(), retval, 10);
    }

    return retval;
}

template <>
uint8_t
strToNum<uint8_t>(vespalib::stringref str) {
    return strToInt<uint16_t>(str);
}

template <>
int8_t
strToNum<int8_t>(vespalib::stringref str) {
    return strToInt<int16_t>(str);
}

template double   strToNum<double>(vespalib::stringref str);
template float    strToNum<float>(vespalib::stringref str);

template <> uint16_t strToNum<uint16_t>(vespalib::stringref str) { return strToInt<uint16_t>(str); }
template <> uint32_t strToNum<uint32_t>(vespalib::stringref str) { return strToInt<uint32_t>(str); }
template <> uint64_t strToNum<uint64_t>(vespalib::stringref str) { return strToInt<uint64_t>(str); }
template <> int16_t  strToNum<int16_t>(vespalib::stringref str) { return strToInt<int16_t>(str); }
template <> int32_t  strToNum<int32_t>(vespalib::stringref str) { return strToInt<int32_t>(str); }
template <> int64_t  strToNum<int64_t>(vespalib::stringref str) { return strToInt<int64_t>(str); }

}
