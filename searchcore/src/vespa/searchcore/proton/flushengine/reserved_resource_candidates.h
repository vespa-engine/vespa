// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>
#include <optional>
#include <vector>

namespace proton::flushengine {

/*
 * Candidates for reserved resource for flush used to calculate worst case need for the reserved resource.
 */
template <typename ResourceT>
class ReservedResourceCandidates {
public:
    using ResourceType = ResourceT;

private:
    /*
     * Candidate for tracking reserved resource for flush, used to calculate worst case need for the reserved
     * resource. The number of total flush threads determines how many candidates to use.
     */
    class Candidate {
        ResourceType _reserved;
        bool         _high_priority;

    public:
        explicit Candidate(ResourceType reserved_in, bool high_priority_in) noexcept
            : _reserved(reserved_in), _high_priority(high_priority_in) {}
        Candidate() noexcept : Candidate(0, false) {}
        [[nodiscard]] bool operator<(const Candidate& rhs) const noexcept { return _reserved > rhs._reserved; }
        [[nodiscard]] ResourceType reserved() const noexcept { return _reserved; }
        [[nodiscard]] bool high_priority() const noexcept { return _high_priority; }
    };

    size_t                   _concurrent;
    std::vector<Candidate>   _candidates; // Used to calculate worst case for concurrent flushes
    std::optional<Candidate> _fallback_high_priority_candidate;

    [[nodiscard]] bool has_high_priority_candidate() const noexcept;

public:
    ReservedResourceCandidates(size_t concurrent) noexcept;
    ~ReservedResourceCandidates();
    void add_candidate(ResourceType reserved, bool high_priority);
    [[nodiscard]] ResourceType reserved_resource_for_flush();
};

} // namespace proton::flushengine
