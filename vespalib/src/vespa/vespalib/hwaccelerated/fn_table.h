// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "target_info.h"
#include <vespa/vespalib/util/bfloat16.h>
#include <array>
#include <cstdint>
#include <functional>
#include <initializer_list>
#include <span>
#include <vector>
#include <string>
#include <string_view>

namespace vespalib::hwaccelerated::dispatch {

// Function pointer type declarations, since C function pointer syntax leaves a bit to be desired.
using DotProductI8Fn  = int64_t (*)(const int8_t* a,  const int8_t* b,  size_t sz) noexcept;
using DotProductI16Fn = int64_t (*)(const int16_t* a, const int16_t* b, size_t sz) noexcept;
using DotProductI32Fn = int64_t (*)(const int32_t* a, const int32_t* b, size_t sz) noexcept;
using DotProductI64Fn = int64_t (*)(const int64_t* a, const int64_t* b, size_t sz) noexcept;

using DotProductBF16Fn = float (*)(const BFloat16* a, const BFloat16* b, size_t sz) noexcept;
using DotProductF32Fn  = float (*)(const float* a, const float* b, size_t sz) noexcept;
using DotProductF64Fn  = double (*)(const double* a, const double* b, size_t sz) noexcept;

using SquaredEuclideanDistanceI8Fn   = double (*)(const int8_t* a, const int8_t* b, size_t sz) noexcept;
using SquaredEuclideanDistanceBF16Fn = double (*)(const BFloat16* a, const BFloat16* b, size_t sz) noexcept;
using SquaredEuclideanDistanceF32Fn  = double (*)(const float* a, const float* b, size_t sz) noexcept;
using SquaredEuclideanDistanceF64Fn  = double (*)(const double* a, const double* b, size_t sz) noexcept;

using BinaryHammingDistanceFn = size_t (*)(const void* lhs, const void* rhs, size_t sz) noexcept;

using PopulationCountFn = size_t (*)(const uint64_t* buf, size_t sz) noexcept;

using ConvertBFloat16ToFloatFn = void (*)(const uint16_t* src, float* dest, size_t sz) noexcept;

using OrBitFn     = void (*)(void* a, const void* b, size_t bytes) noexcept;
using AndBitFn    = void (*)(void* a, const void* b, size_t bytes) noexcept;
using AndNotBitFn = void (*)(void* a, const void* b, size_t bytes) noexcept;
using NotBitFn    = void (*)(void* a, size_t bytes) noexcept;

using And128Fn = void (*)(size_t offset, const std::vector<std::pair<const void*, bool>>& src, void* dest) noexcept;
using Or128Fn  = void (*)(size_t offset, const std::vector<std::pair<const void*, bool>>& src, void* dest) noexcept;

// Function table containing (possibly nullptr) raw function pointers to vectorization
// function implementations. These pointers must be entirely "freestanding" (i.e. not
// require any explicit `this`-like state) and must be valid for the lifetime of the
// process.
struct FnTable {
    FnTable();
    explicit FnTable(const TargetInfo& prefilled_target_info);
    ~FnTable();

    FnTable(const FnTable&);
    FnTable& operator=(const FnTable&);

    // Important: new entries to the function table must also be added to VESPA_HWACCEL_VISIT_FN_TABLE

    DotProductI8Fn  dot_product_i8  = nullptr;
    DotProductI16Fn dot_product_i16 = nullptr;
    DotProductI32Fn dot_product_i32 = nullptr;
    DotProductI64Fn dot_product_i64 = nullptr;

    DotProductBF16Fn dot_product_bf16 = nullptr;
    DotProductF32Fn  dot_product_f32  = nullptr;
    DotProductF64Fn  dot_product_f64  = nullptr;

    SquaredEuclideanDistanceI8Fn   squared_euclidean_distance_i8   = nullptr;
    SquaredEuclideanDistanceBF16Fn squared_euclidean_distance_bf16 = nullptr;
    SquaredEuclideanDistanceF32Fn  squared_euclidean_distance_f32  = nullptr;
    SquaredEuclideanDistanceF64Fn  squared_euclidean_distance_f64  = nullptr;

    BinaryHammingDistanceFn binary_hamming_distance = nullptr;

    PopulationCountFn population_count = nullptr;

    ConvertBFloat16ToFloatFn convert_bfloat16_to_float = nullptr;

    OrBitFn     or_bit      = nullptr;
    AndBitFn    and_bit     = nullptr;
    AndNotBitFn and_not_bit = nullptr;
    NotBitFn    not_bit     = nullptr;

    And128Fn and_128 = nullptr;
    Or128Fn  or_128  = nullptr;

    enum class FnId {
        DOT_PRODUCT_I8 = 0,
        DOT_PRODUCT_I16,
        DOT_PRODUCT_I32,
        DOT_PRODUCT_I64,
        DOT_PRODUCT_BF16,
        DOT_PRODUCT_F32,
        DOT_PRODUCT_F64,
        SQUARED_EUCLIDEAN_DISTANCE_I8,
        SQUARED_EUCLIDEAN_DISTANCE_BF16,
        SQUARED_EUCLIDEAN_DISTANCE_F32,
        SQUARED_EUCLIDEAN_DISTANCE_F64,
        BINARY_HAMMING_DISTANCE,
        POPULATION_COUNT,
        CONVERT_BFLOAT16_TO_FLOAT,
        OR_BIT,
        AND_BIT,
        AND_NOT_BIT,
        NOT_BIT,
        AND_128,
        OR_128,
        MAX_ID_SENTINEL
    };

    static constexpr size_t NFunctions = static_cast<size_t>(FnId::MAX_ID_SENTINEL);

    static_assert(NFunctions < 64);
    uint64_t suboptimal_fn_mask = 0;
    std::array<TargetInfo, NFunctions> fn_target_infos;

    // Indicate that a particular function exists in this table, but its performance
    // is expected to be suboptimal when compared to "worse" function tables. But the
    // function is still available for testing and benchmarking.
    constexpr void tag_fns_as_suboptimal(std::initializer_list<const FnId> fn_ids) noexcept {
        for (FnId fn_id : fn_ids) {
            suboptimal_fn_mask |= (1ULL << static_cast<uint64_t>(fn_id));
        }
    }

    [[nodiscard]] constexpr bool fn_is_tagged_as_suboptimal(FnId fn_id) const noexcept {
        return (suboptimal_fn_mask & (1ULL << static_cast<uint64_t>(fn_id))) != 0;
    }

    [[nodiscard]] constexpr const TargetInfo& fn_target_info(FnId fn_id) const noexcept {
        return fn_target_infos[static_cast<size_t>(fn_id)];
    }

    // Invokes `callback` with the FnId of each non-nullptr function pointer in this
    // function table.
    void for_each_present_fn(const std::function<void(FnId)>& callback) const;

    [[nodiscard]] bool has_fn(FnId fn_id) const noexcept {
        bool present = false;
        for_each_present_fn([&present, fn_id](FnId present_id) noexcept {
            if (present_id == fn_id) {
                present = true;
            }
        });
        return present;
    }

    [[nodiscard]] std::string to_string() const;

    // Returns true iff all function pointers are non-nullptr
    [[nodiscard]] bool is_complete() const noexcept;

    // Returns a static string containing the name of the function field for a valid
    // FnId. Example: FnId::DOT_PRODUCT_I8 -> "dot_product_i8"
    [[nodiscard]] static std::string_view id_to_fn_name(FnId id) noexcept;
};

// Cheeky function table macro that can be used to avoid having to remember to update
// all places that need to poke at the fields of a function table. The `VISITOR` argument
// is invoked with 3 args; the function ptr type, the function ptr name and its ID used
// by the "is this function suboptimal for this target"-mask and the target info array.
#define VESPA_HWACCEL_VISIT_FN_TABLE(VISITOR) \
    VISITOR(DotProductI8Fn,                 dot_product_i8,                  FnTable::FnId::DOT_PRODUCT_I8)                  \
    VISITOR(DotProductI16Fn,                dot_product_i16,                 FnTable::FnId::DOT_PRODUCT_I16)                 \
    VISITOR(DotProductI32Fn,                dot_product_i32,                 FnTable::FnId::DOT_PRODUCT_I32)                 \
    VISITOR(DotProductI64Fn,                dot_product_i64,                 FnTable::FnId::DOT_PRODUCT_I64)                 \
    VISITOR(DotProductBF16Fn,               dot_product_bf16,                FnTable::FnId::DOT_PRODUCT_BF16)                \
    VISITOR(DotProductF32Fn,                dot_product_f32,                 FnTable::FnId::DOT_PRODUCT_F32)                 \
    VISITOR(DotProductF64Fn,                dot_product_f64,                 FnTable::FnId::DOT_PRODUCT_F64)                 \
    VISITOR(SquaredEuclideanDistanceI8Fn,   squared_euclidean_distance_i8,   FnTable::FnId::SQUARED_EUCLIDEAN_DISTANCE_I8)   \
    VISITOR(SquaredEuclideanDistanceBF16Fn, squared_euclidean_distance_bf16, FnTable::FnId::SQUARED_EUCLIDEAN_DISTANCE_BF16) \
    VISITOR(SquaredEuclideanDistanceF32Fn,  squared_euclidean_distance_f32,  FnTable::FnId::SQUARED_EUCLIDEAN_DISTANCE_F32)  \
    VISITOR(SquaredEuclideanDistanceF64Fn,  squared_euclidean_distance_f64,  FnTable::FnId::SQUARED_EUCLIDEAN_DISTANCE_F64)  \
    VISITOR(BinaryHammingDistanceFn,        binary_hamming_distance,         FnTable::FnId::BINARY_HAMMING_DISTANCE)         \
    VISITOR(PopulationCountFn,              population_count,                FnTable::FnId::POPULATION_COUNT)                \
    VISITOR(ConvertBFloat16ToFloatFn,       convert_bfloat16_to_float,       FnTable::FnId::CONVERT_BFLOAT16_TO_FLOAT)       \
    VISITOR(OrBitFn,                        or_bit,                          FnTable::FnId::OR_BIT)                          \
    VISITOR(AndBitFn,                       and_bit,                         FnTable::FnId::AND_BIT)                         \
    VISITOR(AndNotBitFn,                    and_not_bit,                     FnTable::FnId::AND_NOT_BIT)                     \
    VISITOR(NotBitFn,                       not_bit,                         FnTable::FnId::NOT_BIT)                         \
    VISITOR(And128Fn,                       and_128,                         FnTable::FnId::AND_128)                         \
    VISITOR(Or128Fn,                        or_128,                          FnTable::FnId::OR_128)

// Freestanding global dispatch function pointers declarations.
// These are link-time initialized to the baseline target implementations (which
// will be either from `neon.cpp` or `x64_generic.cpp` depending on architecture),
// then at some unspecified point in time during global constructor invocation
// they will be updated to point to the function that is considered optimal for
// the current run-time platform environment. It is possible to change the function
// table after this point (for testing!), but this must always be performed in a
// single-threaded context to avoid data races (pointer loads are _not_ atomic).

#define VESPA_HWACCEL_DISPATCH_FN_PTR_NAME(name) _g_ ## name

#define VESPA_HWACCEL_DECLARE_DISPATCH_FN_PTR(fn_type, fn_field, fn_id) \
    extern fn_type VESPA_HWACCEL_DISPATCH_FN_PTR_NAME(fn_field);

VESPA_HWACCEL_VISIT_FN_TABLE(VESPA_HWACCEL_DECLARE_DISPATCH_FN_PTR);

// The following functions are defined in iaccelerated.cpp:

// Returns a new function table built from 1-N input function tables in `fn_tables`.
// fn_tables is in "best to worst" order (i.e. best is at front, worst is at back),
// meaning that if a function is present (non-null) in a "better" table, it will be
// preferred over one in a "worse" table, _unless_ the function is tagged as suboptimal
// by the table _and_ `exclude_suboptimal == true`. In the latter case, the function is
// excluded in favor of the one from the technically worse table. This is to avoid
// including functions with known suboptimal performance vs. another "worse" target.
//
// If the union of non-null input function pointers across all input tables is equal
// to the full set of possible function pointers, the returned table will be complete.
//
// It is recommended that the last table of `fn_tables` be a complete table, to ensure
// the returned table is also complete.
//
// Information about suboptimal functions is not preserved in the returned table.
[[nodiscard]] FnTable build_composite_fn_table(std::span<const FnTable> fn_tables, bool exclude_suboptimal) noexcept;

// Convenience function to build a composite table on top of a single other function table
[[nodiscard]] FnTable build_composite_fn_table(const FnTable& fn_table,
                                               const FnTable& base_table,
                                               bool exclude_suboptimal) noexcept;

// Returns the function table that is presumed to be optimal for the architecture the
// process is currently running on.
[[nodiscard]] FnTable optimal_composite_fn_table() noexcept;

// Returns a reference to the globally active function table. Its contents will usually be
// equal to that of `optimal_composite_fn_table()` unless overridden at runtime.
[[nodiscard]] const FnTable& active_fn_table() noexcept;

// This can be used wisely by single-threaded tests and benchmarks to replace the
// entire vectorization world. The function table _must_ be _complete_, i.e. all
// function pointers must be non-nullptr. This function does _not_ fall back to a
// baseline target for unset function pointers.
void thread_unsafe_update_function_dispatch_pointers(const FnTable& fns);

} // vespalib::hwaccelerated::dispatch
