// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "avx2.h"

#include "avxprivate.hpp"
#include "fn_table.h"

namespace vespalib::hwaccelerated {

namespace {
int64_t my_dot_product_i8(const int8_t* a, const int8_t* b, size_t sz) noexcept {
    return helper::multiplyAdd(a, b, sz);
}
double my_squared_euclidean_distance_i8(const int8_t* a, const int8_t* b, size_t sz) noexcept {
    return helper::squaredEuclideanDistance(a, b, sz);
}
double my_squared_euclidean_distance_f32(const float* a, const float* b, size_t sz) noexcept {
    return avx::euclideanDistanceSelectAlignment<float, 32>(a, b, sz);
}
double my_squared_euclidean_distance_f64(const double* a, const double* b, size_t sz) noexcept {
    return avx::euclideanDistanceSelectAlignment<double, 32>(a, b, sz);
}
size_t my_population_count(const uint64_t* buf, size_t sz) noexcept { return helper::populationCount(buf, sz); }
void   my_convert_bfloat16_to_float(const uint16_t* src, float* dest, size_t sz) noexcept {
    helper::convert_bfloat16_to_float(src, dest, sz);
}
void my_and_128(size_t offset, const std::vector<std::pair<const void*, bool>>& src, void* dest) noexcept {
    helper::andChunks<32u, 4u>(offset, src, dest);
}
void my_or_128(size_t offset, const std::vector<std::pair<const void*, bool>>& src, void* dest) noexcept {
    helper::orChunks<32u, 4u>(offset, src, dest);
}
TargetInfo my_target_info() noexcept { return {"AutoVec", "AVX2", 32}; }
} // namespace

namespace {

[[nodiscard]] dispatch::FnTable build_fn_table() {
    dispatch::FnTable ft(my_target_info());
    ft.dot_product_i8 = my_dot_product_i8;
    ft.squared_euclidean_distance_i8 = my_squared_euclidean_distance_i8;
    ft.squared_euclidean_distance_f32 = my_squared_euclidean_distance_f32;
    ft.squared_euclidean_distance_f64 = my_squared_euclidean_distance_f64;
    ft.population_count = my_population_count;
    ft.convert_bfloat16_to_float = my_convert_bfloat16_to_float;
    ft.and_128 = my_and_128;
    ft.or_128 = my_or_128;
    return ft;
}

} // namespace

TargetInfo Avx2Accelerator::target_info() const noexcept { return my_target_info(); }

const dispatch::FnTable& Avx2Accelerator::fn_table() const {
    static const dispatch::FnTable tbl = build_fn_table();
    return tbl;
}

} // namespace vespalib::hwaccelerated
