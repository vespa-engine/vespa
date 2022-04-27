// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <type_traits>

namespace search::multivalue {

template <typename T> class WeightedValue;

/*
 * Check for the presence of a weight.
 */
template <typename T>
struct is_WeightedValue : std::false_type {};

template <typename T>
struct is_WeightedValue<WeightedValue<T>> : std::true_type {};

template <typename T>
inline constexpr bool is_WeightedValue_v = is_WeightedValue<T>::value;

/*
 * Extract inner type.
 */
template <typename T>
struct ValueType { using type = T; };

template <typename T>
struct ValueType<WeightedValue<T>> { using type = T; };

template <typename T>
using ValueType_t = typename ValueType<T>::type;

}
