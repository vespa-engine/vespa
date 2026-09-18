// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.//

#include <vespa/vespalib/util/histogram.hpp>

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <cmath>

using namespace ::testing;

namespace vespalib::histogram {

TEST(HistogramTest, empty_thresholds_implies_single_catch_all_bucket) {
    Histogram<uint64_t> h({});
    ASSERT_EQ(h.bucket_count(), 1);
    ASSERT_EQ(h.bucket_index_of(0), 0);
    ASSERT_EQ(h.bucket_index_of(1), 0);
    ASSERT_EQ(h.bucket_index_of(UINT64_MAX), 0);
    EXPECT_TRUE(h.all_zero());
    h.sample(0);
    EXPECT_EQ(h.bucket_value(0), 1);
    EXPECT_FALSE(h.all_zero());
    h.sample(1);
    EXPECT_EQ(h.bucket_value(0), 2);
    h.sample(UINT64_MAX);
    EXPECT_EQ(h.bucket_value(0), 3);
}

// TODO bin vs bucket...

TEST(HistogramTest, regular_bins_places_thresholds_uniformly_across_interval) {
    EXPECT_THAT(regular_bins(1, 0, 10), IsEmpty());
    EXPECT_THAT(regular_bins(2, 0, 10), ElementsAre(5));
    EXPECT_THAT(regular_bins(10, 0, 10), ElementsAre(1, 2, 3, 4, 5, 6, 7, 8, 9));
    EXPECT_THAT(regular_bins(10, 10, 20), ElementsAre(11, 12, 13, 14, 15, 16, 17, 18, 19));
}

/*
 * Computes the (asymmetric) Kullback-Leibler divergence between the two discrete
 * probability distributions represented by the histograms `p` and `q`.
 *
 * Preconditions:
 *  - `p` and `q` have the same thresholds
 *  - `p` and `q` are normalized (i.e. the sum of their buckets add up to 1)
 *  - `q` has a non-zero probability for all buckets
 *
 * See: https://en.wikipedia.org/wiki/Kullback%E2%80%93Leibler_divergence
 *
 * For a symmetric alternative, see `jensen_shannon_divergence(p, q)`.
 */
[[nodiscard]] double kullback_leibler_divergence(const Histogram<double>& p, const Histogram<double>& q) {
    assert(p.bucket_count() == q.bucket_count());
    double kl_sum = 0;
    // Since thresholds are identical, bucket layout shall also be identical
    for (size_t i = 0; i < p.bucket_count(); ++i) {
        const double p_x = p.bucket_value(i);
        const double p_q = q.bucket_value(i);
        kl_sum += p_x * std::log(p_x / p_q); // TODO choice between bits and nats? (this is in nats)
    }
    return kl_sum;
}

namespace detail {

[[nodiscard]] Histogram<double> mixture_distribution(const Histogram<double>& p, const Histogram<double>& q) {
    assert(p.bucket_count() == q.bucket_count());
    Histogram<double> m(p.thresholds());
    for (size_t i = 0; i < p.bucket_count(); ++i) {
        const double p_x = p.bucket_value(i);
        const double p_q = q.bucket_value(i);
        // TODO is this the correct formulation?
        m.set_bucket_value(i, (p_x + p_q) * 0.5);
    }
    return m;
}

} // namespace detail

/*
 * Computes the symmetric Jensen-Shannon divergence between the two discrete probability
 * distributions `p` and `q`.
 *
 * Precondition: `p` and `q` have the same thresholds
 *
 * See: https://en.wikipedia.org/wiki/Jensen%E2%80%93Shannon_divergence
 */
[[nodiscard]] double jensen_shannon_divergence(const Histogram<double>& p, const Histogram<double>& q) {
    Histogram<double> m = detail::mixture_distribution(p, q);
    if (m.all_zero()) {
        return 0; // Both P and Q have all zero probabilities, so treat this as zero divergence
    }
    return 0.5 * kullback_leibler_divergence(p, m) + 0.5 * kullback_leibler_divergence(q, m);
}

TEST(KullbackLeiblerDivergenceTest, can_compute_kld_between_normalized_distributions) {
    auto t = regular_bins<double>(3, 0, 3);

    Histogram<double> p(t), q(t);
    p.set_bucket_value(0, 9. / 25.);
    p.set_bucket_value(1, 12. / 25.);
    p.set_bucket_value(2, 4. / 25.);

    q.set_bucket_value(0, 1. / 3.);
    q.set_bucket_value(1, 1. / 3.);
    q.set_bucket_value(2, 1. / 3.);

    // p and q are pre-normalized, so we can pass them directly
    const double kld_pq = kullback_leibler_divergence(p, q);
    EXPECT_NEAR(kld_pq, 0.0852996, 0.0000001);
    const double kld_qp = kullback_leibler_divergence(q, p);
    EXPECT_NEAR(kld_qp, 0.097455, 0.000001);
}

} // namespace vespalib::histogram
