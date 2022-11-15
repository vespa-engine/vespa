// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/datastore/entryref.h>
#include <cstdint>
#include <queue>
#include <vector>

namespace search::tensor {

/**
 * Represents a candidate node with its distance to another point in space.
 */
struct HnswCandidate {
    uint32_t nodeid;
    vespalib::datastore::EntryRef node_ref;
    double distance;
    HnswCandidate(uint32_t nodeid_in, double distance_in) noexcept
      : nodeid(nodeid_in), node_ref(), distance(distance_in) {}
    HnswCandidate(uint32_t nodeid_in, vespalib::datastore::EntryRef node_ref_in, double distance_in) noexcept
      : nodeid(nodeid_in), node_ref(node_ref_in), distance(distance_in) {}
};

struct GreaterDistance {
    bool operator() (const HnswCandidate& lhs, const HnswCandidate& rhs) const {
        return (rhs.distance < lhs.distance);
    }
};

struct LesserDistance {
    bool operator() (const HnswCandidate& lhs, const HnswCandidate& rhs) const {
        return (lhs.distance < rhs.distance);
    }
};

using HnswCandidateVector = std::vector<HnswCandidate>;

/**
 * Priority queue that keeps the candidate node that is nearest a point in space on top.
 */
using NearestPriQ = std::priority_queue<HnswCandidate, HnswCandidateVector, GreaterDistance>;

/**
 * Priority queue that keeps the candidate node that is furthest away a point in space on top.
 */
class FurthestPriQ : public std::priority_queue<HnswCandidate, HnswCandidateVector, LesserDistance> {
public:
    const HnswCandidateVector& peek() const { return c; }
};

}

