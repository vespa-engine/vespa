// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/bfloat16.h>
#include <vector>

namespace vespalib::hwaccelerated {

struct FnTable {
    int64_t (*dot_product_i8)(const int8_t* a, const int8_t* b, size_t sz) noexcept = nullptr;
    int64_t (*dot_product_i16)(const int16_t* a, const int16_t* b, size_t sz) noexcept = nullptr;
    int64_t (*dot_product_i32)(const int32_t* a, const int32_t* b, size_t sz) noexcept = nullptr;
    int64_t (*dot_product_i64)(const int64_t* a, const int64_t* b, size_t sz) noexcept = nullptr;

    float  (*dot_product_bf16)(const BFloat16* a, const BFloat16* b, size_t sz) noexcept = nullptr;
    float  (*dot_product_f32)(const float* a, const float* b, size_t sz) noexcept = nullptr;
    double (*dot_product_f64)(const double* a, const double* b, size_t sz) noexcept = nullptr;

    double (*squared_euclidean_distance_i8)(const int8_t* a, const int8_t* b, size_t sz) noexcept = nullptr;
    double (*squared_euclidean_distance_bf16)(const BFloat16* a, const BFloat16* b, size_t sz) noexcept = nullptr;
    double (*squared_euclidean_distance_f32)(const float* a, const float* b, size_t sz) noexcept = nullptr;
    double (*squared_euclidean_distance_f64)(const double* a, const double* b, size_t sz) noexcept = nullptr;

    size_t (*population_count)(const uint64_t* buf, size_t sz) noexcept = nullptr;

    void (*convert_bfloat16_to_float)(const uint16_t* src, float* dest, size_t sz) noexcept = nullptr;

    void (*or_bit)(void* a, const void* b, size_t bytes) noexcept = nullptr;
    void (*and_bit)(void* a, const void* b, size_t bytes) noexcept = nullptr;
    void (*and_not_bit)(void* a, const void* b, size_t bytes) noexcept = nullptr;
    void (*not_bit)(void* a, size_t bytes) noexcept = nullptr;

    // AND 128 bytes from multiple, optionally inverted sources
    void (*and_128)(size_t offset, const std::vector<std::pair<const void*, bool>>& src, void* dest) noexcept = nullptr;
    // OR128 bytes from multiple, optionally inverted sources
    void (*or_128)(size_t offset, const std::vector<std::pair<const void*, bool>>& src, void* dest) noexcept = nullptr;
};

#define VESPA_HWACCEL_VISIT_FN_TABLE(VISITOR) \
    VISITOR(dot_product_i8)                   \
    VISITOR(dot_product_i16)                  \
    VISITOR(dot_product_i32)                  \
    VISITOR(dot_product_i64)                  \
    VISITOR(dot_product_bf16)                 \
    VISITOR(dot_product_f32)                  \
    VISITOR(dot_product_f64)                  \
    VISITOR(squared_euclidean_distance_i8)    \
    VISITOR(squared_euclidean_distance_bf16)  \
    VISITOR(squared_euclidean_distance_f32)   \
    VISITOR(squared_euclidean_distance_f64)   \
    VISITOR(population_count)                 \
    VISITOR(convert_bfloat16_to_float)        \
    VISITOR(or_bit)                           \
    VISITOR(and_bit)                          \
    VISITOR(and_not_bit)                      \
    VISITOR(not_bit)                          \
    VISITOR(and_128)                          \
    VISITOR(or_128)

extern const FnTable* _global_fn_table;

} // vespalib::hwaccelerated
