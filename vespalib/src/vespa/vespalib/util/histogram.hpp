// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "histogram.h"

#include <algorithm>
#include <atomic>
#include <cassert>
#include <cmath>
#include <concepts>
#include <cstdint>
#include <format>
#include <memory>
#include <vector>

namespace vespalib::histogram {

template <typename SampleT, typename StorageT>
Thresholds<SampleT, StorageT>::Thresholds(StorageT in_thresholds) : _storage(std::move(in_thresholds)) {
}

template <typename SampleT, typename StorageT>
Thresholds<SampleT, StorageT>::Thresholds(const Thresholds&) = default;

template <typename SampleT, typename StorageT>
Thresholds<SampleT, StorageT>& Thresholds<SampleT, StorageT>::operator=(const Thresholds&) = default;

template <typename SampleT, typename StorageT>
Thresholds<SampleT, StorageT>::Thresholds(Thresholds&&) noexcept = default;

template <typename SampleT, typename StorageT>
Thresholds<SampleT, StorageT>& Thresholds<SampleT, StorageT>::operator=(Thresholds&&) noexcept = default;

template <typename SampleT, typename StorageT>
Thresholds<SampleT, StorageT>::~Thresholds() = default;

template <typename SampleT, typename BucketAccess, typename ThresholdStorage, typename BucketStorage>
Histogram<SampleT, BucketAccess, ThresholdStorage, BucketStorage>::~Histogram() = default;

template <typename SampleT, typename BucketAccess, typename ThresholdStorage, typename BucketStorage>
std::string Histogram<SampleT, BucketAccess, ThresholdStorage, BucketStorage>::to_string() const {
    SampleT sum = 0;
    for_each_bucket_value([&sum](const auto v) noexcept { sum += v; });
    // TODO auto-adjust alignments based on max number width etc.
    std::string  buf;
    const size_t n = bucket_count();
    for (size_t i = 0; i < n; ++i) {
        if (i == 0) {
            buf += "(-inf  ";
        } else {
            if constexpr (std::is_floating_point_v<SampleT>) {
                std::format_to(std::back_inserter(buf), "({: 6.2f}", _thresholds[i - 1]);
            } else {
                std::format_to(std::back_inserter(buf), "({: 5}", _thresholds[i - 1]);
            }
        }
        if (i == n - 1) {
            buf += ",   +inf";
        } else {
            if constexpr (std::is_floating_point_v<SampleT>) {
                std::format_to(std::back_inserter(buf), ", {: 6.2f}", _thresholds[i]);
            } else {
                std::format_to(std::back_inserter(buf), ", {: 5}", _thresholds[i]);
            }
        }
        const SampleT bv = bucket_value(i);
        if constexpr (std::is_floating_point_v<SampleT>) {
            std::format_to(std::back_inserter(buf), "] {:8.2f} ", bv);
        } else {
            std::format_to(std::back_inserter(buf), "] {:8} ", bv);
        }

        constexpr size_t max_width = 50;
        if (sum > 0) {
            const auto width =
                std::min(static_cast<size_t>(std::round((bv / static_cast<double>(sum)) * max_width)), max_width);
            buf.append(width, '*');
        }
        buf += '\n';
    }
    return buf;
}

template <typename T>
Thresholds<T> regular_bins(const size_t n_bins, const T from, const T to) {
    assert(n_bins > 0);
    assert(to > from);
    if (n_bins == 1) {
        return {}; // no thresholds
    }
    std::vector<T> t(n_bins - 1);
    const T        bin_size = (to - from) / static_cast<T>(n_bins);
    if (bin_size == 0) {
        throw std::range_error("Can't use zero-width histogram bin size");
    }
    // We place a threshold at the half-way point between each bin
    for (size_t i = 1; i < n_bins; ++i) {
        T mid = from + bin_size * static_cast<T>(i);
        t[i - 1] = mid;
    }
    return Thresholds<T>(std::move(t));
}

// FIXME threshold storage type...

template <typename ToType, typename FromType>
Thresholds<ToType> cast_thresholds(const Thresholds<FromType>& t) {
    if constexpr (std::is_same_v<ToType, FromType>) {
        return t;
    }
    std::vector<ToType> t2(t.size());
    for (size_t i = 0; i < t.size(); ++i) {
        const auto v = static_cast<ToType>(t[i]);
        if (i > 0 && v - t2[i - 1] == 0) {
            throw std::domain_error("Threshold type cast resulted in zero-sized bin");
        }
        t2[i] = v;
    }
    return Thresholds<ToType>(std::move(t2));
}

template <typename SampleT, typename BucketAccess, typename ThresholdStorage, typename BucketStorage>
Histogram<double> normalized(const Histogram<SampleT, BucketAccess, ThresholdStorage, BucketStorage>& hist) {
    Histogram<double> norm(cast_thresholds<double>(hist.thresholds()));
    SampleT           sum_samples = 0;
    hist.for_each_bucket_value([&sum_samples](const auto v) noexcept { sum_samples += v; });
    if (sum_samples == 0) {
        return norm; // Zero probability for all buckets
    }
    const double norm_rcp = 1.0 / static_cast<double>(sum_samples);
    for (size_t i = 0; i < hist.bucket_count(); ++i) {
        norm.set_bucket_value(i, static_cast<double>(hist.bucket_value(i)) * norm_rcp);
    }
    return norm;
}

} // namespace vespalib::histogram
