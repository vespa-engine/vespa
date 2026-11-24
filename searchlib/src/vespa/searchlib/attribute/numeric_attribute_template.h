// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "floatbase.h"
#include "integerbase.h"

#include <type_traits>

namespace search {

/*
 * The attribute base template for a numeric value type: IntegerAttributeTemplate<T> for
 * integer types and FloatingPointAttributeTemplate<T> for floating point types.
 */
template <typename T>
using NumericAttributeTemplate =
    std::conditional_t<std::is_integral_v<T>, IntegerAttributeTemplate<T>, FloatingPointAttributeTemplate<T>>;

} // namespace search
