// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "reserved_resource_candidates.h"

#include <algorithm>

namespace proton::flushengine {

template <typename ResourceT>
ReservedResourceCandidates<ResourceT>::ReservedResourceCandidates(size_t concurrent) noexcept
    : _concurrent(concurrent), _candidates(), _fallback_high_priority_candidate() {
}

template <typename ResourceT>
ReservedResourceCandidates<ResourceT>::~ReservedResourceCandidates() = default;

template <typename ResourceT>
bool ReservedResourceCandidates<ResourceT>::has_high_priority_candidate() const noexcept {
    for (auto& candidate : _candidates) {
        if (candidate.high_priority()) {
            return true;
        }
    }
    return false;
}

template <typename ResourceT>
void ReservedResourceCandidates<ResourceT>::add_candidate(ResourceType reserved, bool high_priority) {
    Candidate candidate(reserved, high_priority);
    if (candidate.high_priority()) {
        if (!_fallback_high_priority_candidate.has_value() || _fallback_high_priority_candidate.value() < candidate) {
            _fallback_high_priority_candidate = candidate;
        }
    }
    _candidates.push_back(candidate);
}

template <typename ResourceT>
typename ReservedResourceCandidates<ResourceT>::ResourceType
ReservedResourceCandidates<ResourceT>::reserved_resource_for_flush() {
    if (_concurrent < _candidates.size()) {
        /*
         * Retain the _concurrent biggest candidates. They are later used to calculate reserved
         * resource for flush.
         */
        std::nth_element(_candidates.begin(), _candidates.begin() + _concurrent, _candidates.end());
        _candidates.resize(_concurrent);
    }
    if (_concurrent == _candidates.size() && _concurrent != 0 && !has_high_priority_candidate()) {
        /*
         * One of the flush slots is reserved for high priority flush targets, but none of the retained
         * candidates has a high priority. Drop the worst candidate, then try using the fallback high priority
         * candidate.
         */
        std::nth_element(_candidates.begin(), _candidates.begin() + _concurrent - 1, _candidates.end());
        _candidates.resize(_concurrent - 1);
        if (_fallback_high_priority_candidate.has_value()) {
            _candidates.push_back(_fallback_high_priority_candidate.value());
        }
    }
    ResourceType reserved_resource{};
    for (auto& candidate : _candidates) {
        reserved_resource += candidate.reserved();
    }
    return reserved_resource;
}

} // namespace proton::flushengine
