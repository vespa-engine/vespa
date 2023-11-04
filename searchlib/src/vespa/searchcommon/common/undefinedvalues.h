// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cmath>
#include <limits>
#include <vespa/vespalib/stllike/string.h>

namespace search::attribute {

// for all integers
template <typename T>
constexpr T getUndefined() {
    return std::numeric_limits<T>::min();
}

template <>
inline constexpr float getUndefined<float>() {
    return -std::numeric_limits<float>::quiet_NaN();
}

template <>
inline constexpr double getUndefined<double>() {
    return -std::numeric_limits<double>::quiet_NaN();
}

template <>
inline constexpr const char* getUndefined<const char*>() {
    return "";
}

// for all signed integers
template <typename T>
bool isUndefined(T value) {
    return value == getUndefined<T>();
}

template <>
inline bool isUndefined<uint8_t>(uint8_t) {
    return false;
}

template <>
inline bool isUndefined<uint16_t>(uint16_t) {
    return false;
}

template <>
inline bool isUndefined<uint32_t>(uint32_t) {
    return false;
}

template <>
inline bool isUndefined<uint64_t>(uint64_t) {
    return false;
}

template <>
inline bool isUndefined<float>(float value) {
    return std::isnan(value);
}

template <>
inline bool isUndefined<double>(double value) {
    return std::isnan(value);
}

inline bool isUndefined(const char * value) {
    return (value == nullptr) || (value[0] == '\0');
}

}
