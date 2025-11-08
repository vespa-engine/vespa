// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#ifndef VESPA_HWACCEL_TARGET_TYPE
#error "VESPA_HWACCEL_TARGET_TYPE not set"
#endif
#ifndef VESPA_HWACCEL_INCLUDE_DEFINITIONS
#error "VESPA_HWACCEL_INCLUDE_DEFINITIONS not set"
#endif

#include "fn_table.h"
#include "private_helpers.hpp"
#include <cblas.h>

namespace vespalib::hwaccelerated {

namespace {

template <typename ACCUM, typename T, size_t UNROLL>
ACCUM
multiplyAdd(const T * a, const T * b, size_t sz) noexcept
{
    ACCUM partial[UNROLL];
    for (size_t i(0); i < UNROLL; i++) {
        partial[i] = 0;
    }
    size_t i(0);
    for (; i + UNROLL <= sz; i+= UNROLL) {
        for (size_t j(0); j < UNROLL; j++) {
            partial[j] += a[i+j] * b[i+j];
        }
    }
    for (;i < sz; i++) {
        partial[i%UNROLL] += a[i] * b[i];
    }
    ACCUM sum(0);
    for (size_t j(0); j < UNROLL; j++) {
        sum += partial[j];
    }
    return sum;
}

template <typename AccuT, typename T, size_t UNROLL>
AccuT
squaredEuclideanDistanceT(const T * a, const T * b, size_t sz) noexcept
{
    AccuT partial[UNROLL];
    for (size_t i(0); i < UNROLL; i++) {
        partial[i] = 0;
    }
    size_t i(0);
    for (; i + UNROLL <= sz; i += UNROLL) {
        for (size_t j(0); j < UNROLL; j++) {
            AccuT d = a[i+j] - b[i+j];
            partial[j] += d * d;
        }
    }
    for (;i < sz; i++) {
        AccuT d = a[i] - b[i];
        partial[i%UNROLL] += d * d;
    }
    double sum(0);
    for (size_t j(0); j < UNROLL; j++) {
        sum += partial[j];
    }
    return sum;
}

template<size_t UNROLL, typename Operation>
void
bitOperation(Operation operation, void * aOrg, const void * bOrg, size_t bytes) noexcept {

    const size_t sz(bytes/sizeof(uint64_t));
    {
        auto a(static_cast<uint64_t *>(aOrg));
        auto b(static_cast<const uint64_t *>(bOrg));
        size_t i(0);
        for (; i + UNROLL <= sz; i += UNROLL) {
            for (size_t j(0); j < UNROLL; j++) {
                a[i + j] = operation(a[i + j], b[i + j]);
            }
        }
        for (; i < sz; i++) {
            a[i] = operation(a[i], b[i]);
        }
    }

    auto a(static_cast<uint8_t *>(aOrg));
    auto b(static_cast<const uint8_t *>(bOrg));
    for (size_t i(sz*sizeof(uint64_t)); i < bytes; i++) {
        a[i] = operation(a[i], b[i]);
    }
}

int64_t my_dot_product_i8(const int8_t* a, const int8_t* b, size_t sz) noexcept {
    return helper::multiplyAdd(a, b, sz);
}
int64_t my_dot_product_i16(const int16_t* a, const int16_t* b, size_t sz) noexcept {
    return multiplyAdd<int64_t, int16_t, 8>(a, b, sz);
}
int64_t my_dot_product_i32(const int32_t* a, const int32_t* b, size_t sz) noexcept {
    return multiplyAdd<int64_t, int32_t, 8>(a, b, sz);
}
int64_t my_dot_product_i64(const int64_t* a, const int64_t* b, size_t sz) noexcept {
    return multiplyAdd<long long, int64_t, 8>(a, b, sz);
}
float my_dot_product_bf16(const BFloat16* a, const BFloat16* b, size_t sz) noexcept {
    return multiplyAdd<float, BFloat16, 16>(a, b, sz);
}
float my_dot_product_f32(const float* a, const float* b, size_t sz) noexcept {
    return cblas_sdot(sz, a, 1, b, 1);
}
double my_dot_product_f64(const double* a, const double* b, size_t sz) noexcept {
    return cblas_ddot(sz, a, 1, b, 1);
}
double my_squared_euclidean_distance_i8(const int8_t* a, const int8_t* b, size_t sz) noexcept {
    return helper::squaredEuclideanDistance(a, b, sz);
}
double my_squared_euclidean_distance_bf16(const BFloat16* a, const BFloat16* b, size_t sz) noexcept {
    // This is around 10x the perf of the naive loop in mixed_l2_distance.cpp
    return squaredEuclideanDistanceT<float, BFloat16, 16>(a, b, sz);
}
double my_squared_euclidean_distance_f32(const float* a, const float* b, size_t sz) noexcept {
    return squaredEuclideanDistanceT<float, float, 16>(a, b, sz);
}
double my_squared_euclidean_distance_f64(const double* a, const double* b, size_t sz) noexcept {
    return squaredEuclideanDistanceT<double, double, 16>(a, b, sz);
}
size_t my_binary_hamming_distance(const void* lhs, const void* rhs, size_t sz) noexcept {
    return helper::autovec_binary_hamming_distance(lhs, rhs, sz);
}
size_t my_population_count(const uint64_t* buf, size_t sz) noexcept {
    return helper::populationCount(buf, sz);
}
void my_convert_bfloat16_to_float(const uint16_t* src, float* dest, size_t sz) noexcept {
    helper::convert_bfloat16_to_float(src, dest, sz);
}
void my_or_bit(void* aOrg, const void* bOrg, size_t bytes) noexcept {
    bitOperation<8>([](uint64_t a, uint64_t b) { return a | b; }, aOrg, bOrg, bytes);
}
void my_and_bit(void* aOrg, const void* bOrg, size_t bytes) noexcept {
    bitOperation<8>([](uint64_t a, uint64_t b) { return a & b; }, aOrg, bOrg, bytes);
}
void my_and_not_bit(void* aOrg, const void* bOrg, size_t bytes) noexcept {
    bitOperation<8>([](uint64_t a, uint64_t b) { return a & ~b; }, aOrg, bOrg, bytes);
}
void my_not_bit(void* aOrg, size_t bytes) noexcept {
    auto a(static_cast<uint64_t *>(aOrg));
    const size_t sz(bytes/sizeof(uint64_t));
    for (size_t i(0); i < sz; i++) {
        a[i] = ~a[i];
    }
    auto ac(static_cast<uint8_t *>(aOrg));
    for (size_t i(sz*sizeof(uint64_t)); i < bytes; i++) {
        ac[i] = ~ac[i];
    }
}
void my_and_128(size_t offset, const std::vector<std::pair<const void*, bool>>& src, void* dest) noexcept {
    helper::andChunks<16, 8>(offset, src, dest);
}
void my_or_128(size_t offset, const std::vector<std::pair<const void*, bool>>& src, void* dest) noexcept {
    helper::orChunks<16, 8>(offset, src, dest);
}
constexpr uint16_t baseline_vector_bytes() noexcept {
#if defined(__AVX512F__)
    return 64;
#elif defined(__AVX2__)
    return 32;
#else
    // Assume 128 bits for aarch64 NEON and < AVX2 x64
    return 16;
#endif
}
TargetInfo my_target_info() noexcept {
    return {"AutoVec", VESPA_HWACCEL_TARGET_NAME, baseline_vector_bytes()};
}

} // anon ns

namespace {

#define VESPA_HWACCEL_ASSIGN_TABLE_FN_VISITOR(fn_type, fn_field, fn_id) \
    ft.fn_field = my_ ## fn_field;

[[nodiscard]] dispatch::FnTable build_fn_table() {
    dispatch::FnTable ft(my_target_info());
    // Use function table visitor macro to ensure we don't miss including any baseline implementations
    VESPA_HWACCEL_VISIT_FN_TABLE(VESPA_HWACCEL_ASSIGN_TABLE_FN_VISITOR);
    return ft;
}

} // anon ns

TargetInfo
VESPA_HWACCEL_TARGET_TYPE::target_info() const noexcept {
    return my_target_info();
}

const dispatch::FnTable&
VESPA_HWACCEL_TARGET_TYPE::fn_table() const {
    static const dispatch::FnTable tbl = build_fn_table();
    return tbl;
}

#ifdef VESPA_HWACCEL_DEFINE_BASELINE_DISPATCH_FN_PTRS

// Create _definitions_ of the extern declarations in fn_table.h that will initially
// point to our baseline implementations.

namespace dispatch {

#define VESPA_HWACCEL_MY_BASELINE_FN_PTR_NAME(name) my_ ## name

#define VESPA_HWACCEL_DEFINE_DISPATCH_FN_PTR(fn_type, fn_field, fn_id) \
    fn_type VESPA_HWACCEL_DISPATCH_FN_PTR_NAME(fn_field) = VESPA_HWACCEL_MY_BASELINE_FN_PTR_NAME(fn_field);

VESPA_HWACCEL_VISIT_FN_TABLE(VESPA_HWACCEL_DEFINE_DISPATCH_FN_PTR);

}

#endif // VESPA_HWACCEL_DEFINE_BASELINE_DISPATCH_FN_PTRS

} // vespalib::hwaccelerated
