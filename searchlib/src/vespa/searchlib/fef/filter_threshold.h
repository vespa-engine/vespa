// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::fef {

/**
 * Class representing the threshold of whether a field should be considered a filter or not during query evaluation.
 *
 * Some fields are always considered filters, while others are only considered filters
 * if the relative document frequency of the term searching the field is above the specified threshold.
 */
class FilterThreshold {
private:
    // A number in the range [0.0, 1.0] encapsulating whether a field should be considered a filter or not.
    float _threshold;

public:
    explicit FilterThreshold() noexcept : _threshold(1.0) { }
    explicit FilterThreshold(bool is_filter_in) noexcept : _threshold(is_filter_in ? 0.0 : 1.0) { }
    explicit FilterThreshold(float threshold) noexcept : _threshold(threshold) { }
    explicit FilterThreshold(double threshold) noexcept : _threshold(threshold) { }
    float threshold() const noexcept { return _threshold; }
    bool is_filter() const noexcept { return _threshold == 0.0; }

    /**
     * Returns whether this is considered a filter for a query term with the given relative document frequency (in the range [0.0, 1.0]).
     */
    bool is_filter(float rel_doc_freq) const noexcept { return rel_doc_freq > _threshold; }
};

}
