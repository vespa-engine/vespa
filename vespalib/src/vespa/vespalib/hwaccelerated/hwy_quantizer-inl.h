// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

// Avoid include guard linter warnings
#ifndef VESPA_HWY_QUANTIZER_INL_H_CALM_DOWN_LINTER
#define VESPA_HWY_QUANTIZER_INL_H_CALM_DOWN_LINTER
#endif

// Per-target include guard. See https://google.github.io/highway/en/master/faq.html Q5.4.
#if defined(VESPA_HWY_QUANTIZER_INL_H_TARGET) == defined(HWY_TARGET_TOGGLE)
#ifdef VESPA_HWY_QUANTIZER_INL_H_TARGET
#undef VESPA_HWY_QUANTIZER_INL_H_TARGET
#else
#define VESPA_HWY_QUANTIZER_INL_H_TARGET
#endif

#include <hwy/highway.h>

HWY_BEFORE_NAMESPACE();
namespace vespalib::hwaccelerated { // NOLINT: must nest namespaces
namespace HWY_NAMESPACE {

namespace hn = hwy::HWY_NAMESPACE;

/*
 * Quantizer for mapping float32 values to the indexes of the quantization buckets whose
 * range they fall into. For performance reasons(tm) there are certain requirements and
 * restrictions on the input bucket and quantization output index distributions:
 *
 *   - There must be a bucket threshold exactly at zero.
 *   - The bucket thresholds must be fully symmetric around zero.
 *   - Buckets are entirely adjacent, i.e. there exists a scalar threshold that lies
 *     exactly at the boundary between any two buckets.
 *   - The leftmost/rightmost buckets have start/end thresholds at -/+ Infinity, respectively.
 *   - The quantization index of a bucket is equal to the number of buckets between it
 *     and the zero threshold, plus a bias. The bias is 0 for buckets <= 0 and #buckets/2
 *     for buckets > 0. E.g. for 16 buckets, the first bucket < 0 has index 0 and the
 *     first positive bucket has index 8. See the rationale for this further down.
 *
 * Because of the known (required) symmetry, only one side of the distribution needs to
 * be specified; the other side is implicitly present.
 *
 * Likewise, the zero-threshold is considered to be always present and is not specified
 * explicitly. Same goes for the -Inf/+Inf boundary thresholds; everything beyond the
 * greatest threshold in either direction ends up the farthest bucket in that direction.
 *
 * For bucket count > 2 (i.e. not just thresholded around zero) an array of thresholds
 * in strictly increasing value must be provided. Each threshold array value represents
 * that threshold's _absolute_ distance from zero. Due to the symmetry, there is an
 * implicit corresponding negative threshold < 0.
 *
 * The quantization index of any given input floating point is value is inferred from
 * the number of distance thresholds that its _absolute_ value crosses, plus a bias
 * derived from the input sign. This works regardless of whether its negative or positive,
 * so we only have to compare for half the number of bucket thresholds. This is why the
 * output indexes have their peculiar ordering.
 *
 * These threshold comparison checks are independent and can largely run in parallel in
 * the CPU pipeline. But we still have to aggregate the _number_ of threshold crossings,
 * i.e. the comparison outputs.
 *
 * Counting threshold crossings can be trivially done as a right/left folding operation,
 * but that introduces a linear dependency chain where each addition needs the result of
 * the previous addition to be ready. Instead, we recursively build what's essentially
 * a reduction tree, where partial results flow up to the root sum. The compiler and CPU
 * can then reorder the leaf computations and partial sums with a lot more freedom. It
 * is technically possible that the compiler will do arbitrary reordering of u8 vector
 * additions already (since they commute with overflow), but doing this explicitly means
 * we don't have to cross our fingers and hope for the best.
 *
 * Numerical precision note: a value that lies exactly at the boundary (threshold) between
 * two buckets gets assigned the index of the bucket closest to zero, _unless_ the value
 * lies exactly _at_ zero, in which case the sign bit decides (since FP zero is signed).
 *
 * Quantization example:
 *
 *   A particular 4-bucket distribution has 5 thresholds of `[-Inf, -3, 0, 3, +Inf]`.
 *
 *   As mentioned above, 4 ouf 5 of these are implicit (+/-Inf, 0 or by symmetry), so
 *   this is represented by the _absolute_ threshold array of just `[3]`.
 *
 *   Example input -> output index mappings:
 *     -100     -> 1
 *     -3.00001 -> 1
 *     -3       -> 0
 *     -2.99999 -> 0
 *     -0.00001 -> 0
 *     -0       -> 0
 *     +0       -> 2
 *     0.000001 -> 2
 *     3        -> 2
 *     3.000001 -> 3
 *     100      -> 3
 *
 * Future note: the threshold array can be replaced with a variadic template <float...>
 * argument once we're on C++26 and have argument pack indexing support. The bucket count
 * can then be inferred directly from the size of this argument pack, avoiding any
 * potential for accidental mismatches.
 */
class ZeroSymmetricQuantizer {
    template <size_t Lbound, size_t Ubound, typename DF32, typename DU8>
    [[nodiscard]]
    HWY_API auto count_crossed_thresholds(const DF32 df32, const DU8 du8, const hn::Vec<DF32> v_abs,
                                          const float* t) noexcept {
        using VU8 = hn::Vec<DU8>;
        using MF32 = hn::Mask<DF32>;

        if constexpr (Lbound == Ubound - 1) { // Leaf case
            constexpr size_t Idx = Lbound;
            // All threshold array accesses are as-if constexpr and will be optimized as such.
            // Constant vectors will also be hoisted out and reused.
            const MF32 is_gt_threshold = hn::Gt(v_abs, hn::Set(df32, t[Idx]));
            return hn::IfThenElseZero(hn::DemoteMaskTo(du8, df32, is_gt_threshold), hn::Set(du8, 1));
        } else { // Inner node case
            static_assert(Lbound < Ubound);
            constexpr size_t half = (Ubound - Lbound) / 2;

            const VU8 lhs = count_crossed_thresholds<Lbound, Lbound + half>(df32, du8, v_abs, t);
            if constexpr (half > 0) {
                const VU8 rhs = count_crossed_thresholds<Lbound + half, Ubound>(df32, du8, v_abs, t);
                return hn::Add(lhs, rhs);
            } else {
                return lhs;
            }
        }
    }

public:
    template <size_t NumBuckets, typename DF32, typename DU8>
    [[nodiscard]]
    HWY_API auto quantize_into_buckets(const DF32 df32, const DU8 du8, const hn::Vec<DF32> v,
                                       const float* t) noexcept {
        using VF32 = hn::Vec<DF32>;
        using VU8 = hn::Vec<DU8>;
        using MU8 = hn::Mask<DU8>;

        static_assert(NumBuckets >= 2);
        constexpr size_t PositiveBuckets = NumBuckets / 2;

        const MU8 is_negative = hn::DemoteMaskTo(du8, df32, hn::IsNegative(v));
        const VU8 bias = hn::IfThenZeroElse(is_negative, hn::Set(du8, PositiveBuckets));
        if constexpr (PositiveBuckets > 1) {
            const VF32 v_abs = hn::Abs(v);
            // The zero-threshold is always handled explicitly above, so it's ignored
            const VU8 crossings = count_crossed_thresholds<0, PositiveBuckets - 1>(df32, du8, v_abs, t);
            return hn::Add(bias, crossings);
        } else {
            return bias;
        }
    }
};

} // namespace HWY_NAMESPACE
} // namespace vespalib::hwaccelerated
HWY_AFTER_NAMESPACE();

#endif // VESPA_HWY_QUANTIZER_INL_H_TARGET
