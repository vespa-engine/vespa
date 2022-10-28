// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace search::tensor {

/*
 * Attribute version determines format of saved attributes.
 */
inline constexpr uint32_t TENSOR_ATTRIBUTE_VERSION = 0;
inline constexpr uint32_t DENSE_TENSOR_ATTRIBUTE_VERSION = 1;

/*
 * Values used to determine if a tensor is present or not for dense tensor attributes
 * (cf. DENSE_TENSOR_ATTRIBUTE_VERSION) that otherwise has fixed size for a saved tensor.
 */
inline constexpr uint8_t tensorIsNotPresent = 0;
inline constexpr uint8_t tensorIsPresent = 1;

}
