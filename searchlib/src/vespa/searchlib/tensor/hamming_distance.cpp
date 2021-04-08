// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hamming_distance.h"

using vespalib::typify_invoke;
using vespalib::eval::TypifyCellType;

namespace search::tensor {

namespace {

struct CalcHamming {
    template <typename LCT, typename RCT>
    static double invoke(const vespalib::eval::TypedCells& lhs,
                         const vespalib::eval::TypedCells& rhs)
    {
        auto lhs_vector = lhs.unsafe_typify<LCT>();
        auto rhs_vector = rhs.unsafe_typify<RCT>();
        size_t sz = lhs_vector.size();
        assert(sz == rhs_vector.size());
        size_t sum = 0;
        for (size_t i = 0; i < sz; ++i) {
            sum += (lhs_vector[i] == rhs_vector[i]) ? 0 : 1;
        }
        return (double)sum;
    }
};

}

double
HammingDistance::calc(const vespalib::eval::TypedCells& lhs,
                      const vespalib::eval::TypedCells& rhs) const
{
    constexpr auto expected = vespalib::eval::CellType::INT8;
    if (__builtin_expect((lhs.type == expected && rhs.type == expected), true)) {
        const uint64_t *words_a = static_cast<const uint64_t *>(lhs.data);
        const uint64_t *words_b = static_cast<const uint64_t *>(rhs.data);
        size_t sum = 0;
        size_t sz = lhs.size;
        assert(sz == rhs.size);
        size_t i = 0;
        for (; i * 8 < sz; ++i) {
            uint64_t xor_bits = words_a[i] ^ words_b[i];
            sum += __builtin_popcountl(xor_bits);
        }
        if (__builtin_expect((i * 8 < sz), false)) {
            const uint8_t *bytes_a = static_cast<const uint8_t *>(lhs.data);
            const uint8_t *bytes_b = static_cast<const uint8_t *>(rhs.data);
            for (i *= 8; i < sz; ++i) {
                uint64_t xor_bits = bytes_a[i] ^ bytes_b[i];
                sum += __builtin_popcountl(xor_bits);
            }
        }
        return (double)sum;
    } else {
        return typify_invoke<2,TypifyCellType,CalcHamming>(lhs.type, rhs.type, lhs, rhs);
    }
}

}
