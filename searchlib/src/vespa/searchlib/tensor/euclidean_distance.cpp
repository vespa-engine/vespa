// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "euclidean_distance.h"
#include <emmintrin.h>
#include <smmintrin.h>
#include <xmmintrin.h>

using vespalib::typify_invoke;
using vespalib::eval::TypifyCellType;

namespace search::tensor {

namespace {

struct CalcEuclidean {
    template <typename LCT, typename RCT>
    static double invoke(const vespalib::eval::TypedCells& lhs,
                         const vespalib::eval::TypedCells& rhs)
    {
        auto lhs_vector = lhs.unsafe_typify<LCT>();
        auto rhs_vector = rhs.unsafe_typify<RCT>();
        double sum = 0.0;
        size_t sz = lhs_vector.size();
        assert(sz == rhs_vector.size());
        for (size_t i = 0; i < sz; ++i) {
            double diff = lhs_vector[i] - rhs_vector[i];
            sum += diff*diff;
        }
        return sum;
    }
};

}

double
SquaredEuclideanDistance::calc(const vespalib::eval::TypedCells& lhs,
                               const vespalib::eval::TypedCells& rhs) const
{
    return typify_invoke<2,TypifyCellType,CalcEuclidean>(lhs.type, rhs.type, lhs, rhs);
}

double
SquaredEuclideanDistance::calc_with_limit(const vespalib::eval::TypedCells& lhs,
                                          const vespalib::eval::TypedCells& rhs,
                                          double) const
{
    // maybe optimize this:
    return typify_invoke<2,TypifyCellType,CalcEuclidean>(lhs.type, rhs.type, lhs, rhs);
}

namespace {

template<typename T> void dumpRegister(T x) {
    static_assert(sizeof(x) <= 64);
    unsigned char buf[64];
    memcpy(buf, &x, sizeof(x));
    for (size_t i = 0; i < sizeof(x); i++) {
        fprintf(stderr, "%02X", buf[i]);
    }
    fprintf(stderr, "\n");
}

__m128 hw_4x_sqe(const void *l, const void *r) {
    uint32_t a_pi8 = * static_cast<const uint32_t *>(l);
    uint32_t b_pi8 = * static_cast<const uint32_t *>(r);

    __m128i a_pi8_m = _mm_set_epi32(0, 0, 0, a_pi8);
    __m128i b_pi8_m = _mm_set_epi32(0, 0, 0, b_pi8);

    __m128i a_pi32 = _mm_cvtepi8_epi32(a_pi8_m);
    __m128i b_pi32 = _mm_cvtepi8_epi32(b_pi8_m);

    __m128i diff_pi32 = _mm_sub_epi32(a_pi32, b_pi32);
    __m128 diff_ps = _mm_cvtepi32_ps(diff_pi32);
    __m128 sq_ps = _mm_mul_ps(diff_ps, diff_ps);
    return sq_ps;
}

} // namespace

double
SquaredEuclideanDistancePI8::calc(const vespalib::eval::TypedCells& lhs,
                                  const vespalib::eval::TypedCells& rhs) const
{
    constexpr auto expected = vespalib::eval::CellType::INT8;
    if (__builtin_expect((lhs.type == expected && rhs.type == expected), true)) {
        __v4sf vec_sum = { 0 };
        auto a = static_cast<const int8_t *>(lhs.data);
        auto b = static_cast<const int8_t *>(rhs.data);
        size_t i = 0;
        for (; i + 3 < lhs.size; i += 4) {
            __m128 sq_4_ps = hw_4x_sqe(a, b);
            vec_sum = _mm_add_ps(vec_sum, sq_4_ps);
            a += 4;
            b += 4;
        }
        float sum = vec_sum[0] + vec_sum[1] + vec_sum[2] + vec_sum[3];
        while (__builtin_expect(i < lhs.size, false)) {
            float diff = float(*a++) - float(*b++);
            ++i;
            sum += diff*diff;
        }
        return sum;
    } else {
        return typify_invoke<2,TypifyCellType,CalcEuclidean>(lhs.type, rhs.type, lhs, rhs);
    }
}


template class SquaredEuclideanDistanceHW<float>;
template class SquaredEuclideanDistanceHW<double>;

}
