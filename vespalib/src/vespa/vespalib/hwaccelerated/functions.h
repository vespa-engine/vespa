// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "fn_table.h"

namespace vespalib::hwaccelerated::vec_fn {

// These are freestanding functions that will be dispatched to the vectorized implementation
// expected to bring the best performance for the currently running CPU architecture.
// Has the expected overhead of a single function pointer indirection, which is pretty much
// as good as it gets for dynamic dispatch. Best of all, no need to carry an IAccelerated
// instance around with you on your journeys.

[[nodiscard]] inline int64_t dot_product(const int8_t* a, const int8_t* b, size_t sz) noexcept {
    return dispatch::VESPA_HWACCEL_DISPATCH_FN_PTR_NAME(dot_product_i8)(a, b, sz);
}

[[nodiscard]] inline int64_t dot_product(const int16_t* a, const int16_t* b, size_t sz) noexcept {
    return dispatch::VESPA_HWACCEL_DISPATCH_FN_PTR_NAME(dot_product_i16)(a, b, sz);
}

[[nodiscard]] inline int64_t dot_product(const int32_t* a, const int32_t* b, size_t sz) noexcept {
    return dispatch::VESPA_HWACCEL_DISPATCH_FN_PTR_NAME(dot_product_i32)(a, b, sz);
}

[[nodiscard]] inline int64_t dot_product(const int64_t* a, const int64_t* b, size_t sz) noexcept {
    return dispatch::VESPA_HWACCEL_DISPATCH_FN_PTR_NAME(dot_product_i64)(a, b, sz);
}

[[nodiscard]] inline float dot_product(const BFloat16* a, const BFloat16* b, size_t sz) noexcept {
    return dispatch::VESPA_HWACCEL_DISPATCH_FN_PTR_NAME(dot_product_bf16)(a, b, sz);
}

[[nodiscard]] inline float dot_product(const float* a, const float* b, size_t sz) noexcept {
    return dispatch::VESPA_HWACCEL_DISPATCH_FN_PTR_NAME(dot_product_f32)(a, b, sz);
}

[[nodiscard]] inline double dot_product(const double* a, const double* b, size_t sz) noexcept {
    return dispatch::VESPA_HWACCEL_DISPATCH_FN_PTR_NAME(dot_product_f64)(a, b, sz);
}

[[nodiscard]] inline double squared_euclidean_distance(const int8_t* a, const int8_t* b, size_t sz) noexcept {
    return dispatch::VESPA_HWACCEL_DISPATCH_FN_PTR_NAME(squared_euclidean_distance_i8)(a, b, sz);
}

[[nodiscard]] inline double squared_euclidean_distance(const BFloat16* a, const BFloat16* b, size_t sz) noexcept {
    return dispatch::VESPA_HWACCEL_DISPATCH_FN_PTR_NAME(squared_euclidean_distance_bf16)(a, b, sz);
}

[[nodiscard]] inline double squared_euclidean_distance(const float* a, const float* b, size_t sz) noexcept {
    return dispatch::VESPA_HWACCEL_DISPATCH_FN_PTR_NAME(squared_euclidean_distance_f32)(a, b, sz);
}

[[nodiscard]] inline double squared_euclidean_distance(const double* a, const double* b, size_t sz) noexcept {
    return dispatch::VESPA_HWACCEL_DISPATCH_FN_PTR_NAME(squared_euclidean_distance_f64)(a, b, sz);
}

[[nodiscard]] inline size_t binary_hamming_distance(const void* a, const void* b, size_t sz) noexcept {
    return dispatch::VESPA_HWACCEL_DISPATCH_FN_PTR_NAME(binary_hamming_distance)(a, b, sz);
}

[[nodiscard]] inline size_t population_count(const uint64_t* buf, size_t sz) noexcept {
    return dispatch::VESPA_HWACCEL_DISPATCH_FN_PTR_NAME(population_count)(buf, sz);
}

inline void convert_bfloat16_to_float(const uint16_t* src, float* dest, size_t sz) noexcept {
    dispatch::VESPA_HWACCEL_DISPATCH_FN_PTR_NAME(convert_bfloat16_to_float)(src, dest, sz);
}

inline void or_bit(void* a, const void* b, size_t bytes) noexcept {
    dispatch::VESPA_HWACCEL_DISPATCH_FN_PTR_NAME(or_bit)(a, b, bytes);
}

inline void and_bit(void* a, const void* b, size_t bytes) noexcept {
    dispatch::VESPA_HWACCEL_DISPATCH_FN_PTR_NAME(and_bit)(a, b, bytes);
}

inline void and_not_bit(void* a, const void* b, size_t bytes) noexcept {
    dispatch::VESPA_HWACCEL_DISPATCH_FN_PTR_NAME(and_not_bit)(a, b, bytes);
}

inline void not_bit(void* a, size_t bytes) noexcept {
    dispatch::VESPA_HWACCEL_DISPATCH_FN_PTR_NAME(not_bit)(a, bytes);
}

// AND 128 bytes from multiple, optionally inverted sources
inline void and_128(size_t offset, const std::vector<std::pair<const void*, bool>>& src, void* dest) noexcept {
    dispatch::VESPA_HWACCEL_DISPATCH_FN_PTR_NAME(and_128)(offset, src, dest);
}

// OR128 bytes from multiple, optionally inverted sources
inline void or_128(size_t offset, const std::vector<std::pair<const void*, bool>>& src, void* dest) noexcept {
    dispatch::VESPA_HWACCEL_DISPATCH_FN_PTR_NAME(or_128)(offset, src, dest);
}

} // vespalib::hwaccelerated
