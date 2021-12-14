// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "compact_buffer_candidate.h"
#include <vector>

namespace vespalib::datastore {

/*
 * Class representing candidate buffers for compaction.
 */
class CompactBufferCandidates {
    std::vector<CompactBufferCandidate> _candidates;
    size_t                            _used;
    size_t                            _dead;
    uint32_t                          _max_buffers;
    double                            _active_buffers_ratio;
    double                            _ratio;
    size_t                            _slack;
    uint32_t                          _free_buffers;
public:
    CompactBufferCandidates(uint32_t num_buffers, uint32_t max_buffers, double active_buffers_ratio, double ratio, size_t slack);
    ~CompactBufferCandidates();
    void add(uint32_t buffer_id, size_t used, size_t dead);
    void set_free_buffers(uint32_t free_buffers);
    void select(std::vector<uint32_t>& buffers);
};

}
