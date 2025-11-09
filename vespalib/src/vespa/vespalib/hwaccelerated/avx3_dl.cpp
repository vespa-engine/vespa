// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "avx3_dl.h"
#include "avxprivate.hpp"
#include "fn_table.h"
#include "x64_generic.h"

namespace vespalib::hwaccelerated {

namespace {

size_t my_population_count(const uint64_t* a, size_t sz) noexcept {
    // When targeting VPOPCNTDQ the compiler auto-vectorization somewhat ironically
    // gets horribly confused when the code is explicitly written to do popcounts
    // in parallel across elements. Just doing a plain, boring loop lets the auto-
    // vectorizer understand the semantics of the loop much more easily.
    size_t count = 0;
    for (size_t i = 0; i < sz; ++i) {
        count += std::popcount(a[i]);
    }
    return count;
}

int64_t my_dot_product_i8(const int8_t* a, const int8_t* b, size_t sz) noexcept {
    return helper::multiplyAdd(a, b, sz);
}
float my_dot_product_f32(const float* a, const float* b, size_t sz) noexcept {
    return avx::dotProductSelectAlignment<float, 64>(a, b, sz);
}
double my_dot_product_f64(const double* a, const double* b, size_t sz) noexcept {
    return avx::dotProductSelectAlignment<double, 64>(a, b, sz);
}
double my_squared_euclidean_distance_i8(const int8_t* a, const int8_t* b, size_t sz) noexcept {
    return helper::squaredEuclideanDistance(a, b, sz);
}
double my_squared_euclidean_distance_f32(const float* a, const float* b, size_t sz) noexcept {
    return avx::euclideanDistanceSelectAlignment<float, 64>(a, b, sz);
}
double my_squared_euclidean_distance_f64(const double* a, const double* b, size_t sz) noexcept {
    return avx::euclideanDistanceSelectAlignment<double, 64>(a, b, sz);
}
size_t my_binary_hamming_distance(const void* lhs, const void* rhs, size_t sz) noexcept {
    return helper::autovec_binary_hamming_distance(lhs, rhs, sz);
}
void my_convert_bfloat16_to_float(const uint16_t* src, float* dest, size_t sz) noexcept {
    helper::convert_bfloat16_to_float(src, dest, sz);
}
void my_and_128(size_t offset, const std::vector<std::pair<const void*, bool>>& src, void* dest) noexcept {
    helper::andChunks<64, 2>(offset, src, dest);
}
void my_or_128(size_t offset, const std::vector<std::pair<const void*, bool>>& src, void* dest) noexcept {
    helper::orChunks<64, 2>(offset, src, dest);
}
TargetInfo my_target_info() noexcept {
    return {"AutoVec", "AVX3_DL", 64};
}

} // anon ns

namespace {

[[nodiscard]] dispatch::FnTable build_fn_table() {
    dispatch::FnTable ft(my_target_info());
    ft.dot_product_i8  = my_dot_product_i8;
    ft.dot_product_f32 = my_dot_product_f32;
    ft.dot_product_f64 = my_dot_product_f64;
    ft.squared_euclidean_distance_i8  = my_squared_euclidean_distance_i8;
    ft.squared_euclidean_distance_f32 = my_squared_euclidean_distance_f32;
    ft.squared_euclidean_distance_f64 = my_squared_euclidean_distance_f64;
    ft.binary_hamming_distance = my_binary_hamming_distance;
    ft.population_count = my_population_count;
    ft.convert_bfloat16_to_float = my_convert_bfloat16_to_float;
    ft.and_128 = my_and_128;
    ft.or_128  = my_or_128;
    return ft;
}

} // anon ns

TargetInfo
Avx3DlAccelerator::target_info() const noexcept {
    return my_target_info();
}

const dispatch::FnTable&
Avx3DlAccelerator::fn_table() const {
    static const dispatch::FnTable tbl = build_fn_table();
    return tbl;
}

}
