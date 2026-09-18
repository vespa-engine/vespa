// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <algorithm>
#include <atomic>
#include <cassert> // TODO move
#include <concepts>
#include <cstdint>
#include <memory>
#include <vector>

namespace vespalib::histogram {

struct NonAtomicBucketAccess {
    template <typename SampleT>
    void inc_bucket(SampleT& bucket) noexcept {
        ++bucket;
    }
    template <typename SampleT>
    void set_bucket_value(SampleT& bucket, SampleT new_value) noexcept {
        bucket = new_value;
    }
    template <typename SampleT>
    SampleT bucket_value(const SampleT& bucket) const noexcept {
        return bucket;
    }
};

// This is only defined for integral types
struct AtomicBucketAccess {
    template <std::integral SampleT>
    void inc_bucket(SampleT& bucket) noexcept {
        std::atomic_ref{bucket}.fetch_add(SampleT{1}, std::memory_order_relaxed);
    }
    template <std::integral SampleT>
    void set_bucket_value(SampleT& bucket, SampleT new_value) noexcept {
        std::atomic_ref{bucket}.store(new_value, std::memory_order_relaxed);
    }
    template <std::integral SampleT>
    SampleT bucket_value(const SampleT& bucket) const noexcept {
        return std::atomic_ref{bucket}.load(std::memory_order_relaxed);
    }
};

// Immutable once constructed.
template <typename SampleT, typename StorageT = std::vector<SampleT>>
class Thresholds {
    StorageT _storage;

public:
    using value_type = SampleT;
    using size_type = size_t;

    Thresholds() noexcept : _storage() {}
    explicit Thresholds(StorageT in_thresholds);
    template <typename Iter>
    Thresholds(Iter first, Iter last) : _storage(first, last) {}
    Thresholds(const Thresholds&);
    Thresholds& operator=(const Thresholds&);
    Thresholds(Thresholds&&) noexcept;
    Thresholds& operator=(Thresholds&&) noexcept;
    ~Thresholds();

    [[nodiscard]] bool empty() const noexcept { return _storage.empty(); }
    [[nodiscard]] size_t size() const noexcept { return _storage.size(); }
    [[nodiscard]] auto begin() const noexcept { return _storage.begin(); }
    [[nodiscard]] auto cbegin() const noexcept { return _storage.cbegin(); }
    [[nodiscard]] auto end() const noexcept { return _storage.end(); }
    [[nodiscard]] auto cend() const noexcept { return _storage.cend(); }
    [[nodiscard]] SampleT operator[](size_t idx) const noexcept { return _storage[idx]; }
    [[nodiscard]] bool operator==(const Thresholds& rhs) const noexcept = default;
};

template <typename T>
[[nodiscard]] Thresholds<T> regular_bins(size_t n_bins, T from, T to);

// Throws std::domain_error if one or more thresholds in the target type result in
// a zero-sized bucket (e.g. casting float -> int with small increments).
template <typename ToType, typename FromType>
[[nodiscard]] Thresholds<ToType> cast_thresholds(const Thresholds<FromType>& t);

template <typename SampleT, typename BucketAccess = NonAtomicBucketAccess,
          typename ThresholdStorage = std::vector<SampleT>, typename BucketStorage = std::vector<SampleT>>
class Histogram {
    using ThresholdsT = Thresholds<SampleT, ThresholdStorage>;

    [[no_unique_address]] BucketAccess _bucket_access;
    ThresholdsT                        _thresholds;
    BucketStorage                      _buckets; // size is |thresholds| + 1

public:
    explicit Histogram(ThresholdsT thresholds)
        : _bucket_access(), _thresholds(std::move(thresholds)), _buckets(_thresholds.size() + 1) {}

    template <typename ThresholdIter>
    Histogram(ThresholdIter first_threshold, ThresholdIter last_threshold)
        : _bucket_access(), _thresholds(first_threshold, last_threshold), _buckets(_thresholds.size() + 1) {}

    ~Histogram();

    // Thresholds specify the inclusive upper bound for that bucket's interval.
    // They are only specified for the boundaries _between_ buckets, so the first
    // bucket then has an implicit _lower_ bound of -Inf, whereas the last bucket
    // has an implicit _upper_ bound of +Inf.
    [[nodiscard]] size_t bucket_index_of(const SampleT s) const noexcept {
        const auto first = std::cbegin(_thresholds);
        const auto end = std::cend(_thresholds);
        // TODO if we know buckets are regularly spaced, can do as O(1)
        auto iter = std::lower_bound(first, end, s);
        if (iter != end) [[likely]] {
            return std::distance(first, iter);
        } // else: belongs in last bucket
        return _buckets.size() - 1;
    }

    // Increments the value of the bucket whose bounds `s` falls into by 1.
    void sample(SampleT s) noexcept {
        const size_t idx = bucket_index_of(s);
        _bucket_access.inc_bucket(_buckets[idx]);
    }
    // TODO [[nodiscard]] SampleT quantile_at(double) const noexcept;

    // We don't directly expose an iterable range of the bucket storage, since access
    // to each element must go via the bucket access policy (e.g. it may require going
    // via a relaxed atomic load).

    [[nodiscard]] size_t bucket_count() const noexcept { return _buckets.size(); }
    [[nodiscard]] SampleT bucket_value(const size_t bucket_idx) const noexcept {
        assert(bucket_idx < bucket_count());
        return _bucket_access.bucket_value(_buckets[bucket_idx]);
    }
    void set_bucket_value(const size_t bucket_idx, const SampleT new_value) noexcept {
        assert(bucket_idx < bucket_count());
        _bucket_access.set_bucket_value(_buckets[bucket_idx], new_value);
    }
    template <typename Fn>
    void for_each_bucket_value(Fn fn) const noexcept(noexcept(fn(std::declval<SampleT>()))) {
        for (size_t i = 0; i < bucket_count(); ++i) {
            fn(bucket_value(i));
        }
    }
    [[nodiscard]] const ThresholdsT& thresholds() const noexcept { return _thresholds; }
    [[nodiscard]] bool all_zero() const noexcept {
        for (size_t i = 0; i < bucket_count(); ++i) {
            if (bucket_value(i) != 0) {
                return false;
            }
        }
        return true;
    }

    [[nodiscard]] std::string to_string() const;
};

/*
 * Takes in an arbitrary histogram `hist` and returns a new histogram where each bucket
 * value has been normalized so that the sum of all bucket values is 1 (modulo floating
 * point precision issues).
 *
 * One common use-case for such normalized histograms is to represent the probability
 * distribution of the original histogram.
 *
 * This function casually assumes that the [sum of] sample counts of all buckets in `hist`
 * can be represented as a _finite_ double value.
 */
template <typename SampleT, typename BucketAccess, typename ThresholdStorage, typename BucketStorage>
[[nodiscard]] Histogram<double> normalized(const Histogram<SampleT, BucketAccess, BucketStorage>& hist);

} // namespace vespalib::histogram
