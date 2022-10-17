// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "compact_buffer_candidates.h"
#include <algorithm>
#include <cmath>

namespace vespalib::datastore {

CompactBufferCandidates::CompactBufferCandidates(uint32_t num_buffers, uint32_t max_buffers, double active_buffers_ratio, double ratio, size_t slack)
    : _candidates(),
      _used(0),
      _dead(0),
      _max_buffers(std::max(max_buffers, 1u)),
      _active_buffers_ratio(std::min(1.0, std::max(0.0001, active_buffers_ratio))),
      _ratio(ratio),
      _slack(_ratio == 0.0 ? 0u : slack),
      _free_buffers(0)
{
    _candidates.reserve(num_buffers);
}

CompactBufferCandidates::~CompactBufferCandidates() = default;

void
CompactBufferCandidates::add(uint32_t buffer_id, size_t used, size_t dead)
{
    _candidates.emplace_back(buffer_id, used, dead);
    _used += used;
    _dead += dead;
}

void
CompactBufferCandidates::set_free_buffers(uint32_t free_buffers)
{
    _free_buffers = free_buffers;
}

void
CompactBufferCandidates::select(std::vector<uint32_t>& buffers)
{
    if (_candidates.empty()) {
        return;
    }
    /*
     * Calculate a limit of how many buffers to compact at once. Throughput,
     * latency, transient resource usage (memory and address space used for
     * held buffers) and stability must all be considered.
     *
     * We want to compact up to a portion of the active buffers (hence
     * _active_buffers_ratio) but do not want to use up all remaining free
     * buffers during compaction (hence free_buffers_ratio). Cap the limit by
     * [1, _max_buffers] to ensure some but not too much progress.
     */
    constexpr double free_buffers_ratio = 0.2;
    uint32_t active_buffers = _candidates.size();
    uint32_t max_buffers = ceil(std::min(active_buffers * _active_buffers_ratio, _free_buffers * free_buffers_ratio));
    max_buffers = std::max(1u, std::min(max_buffers, _max_buffers));
    if (_candidates.size() > max_buffers) {
        std::nth_element(_candidates.begin(), _candidates.begin() + (max_buffers - 1), _candidates.end());
        _candidates.resize(max_buffers);
    }
    std::sort(_candidates.begin(), _candidates.end());
    size_t remaining_used = _used;
    size_t remaining_dead = _dead;
    for (auto& candidate : _candidates) {
        buffers.emplace_back(candidate.get_buffer_id());
        remaining_used -= candidate.get_used();
        remaining_dead -= candidate.get_dead();
        if ((remaining_dead < _slack) || (remaining_dead <= remaining_used * _ratio)) {
            break;
        }
    }
}

}
