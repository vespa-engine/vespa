// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/datastore/entryref.h>
#include <cstdint>
#include <queue>
#include <vector>

namespace search::tensor {

/**
 * Represents a travesal candidate node with its distance to another
 * point in space.
 */
struct HnswTraversalCandidate {
    uint32_t nodeid;
    vespalib::datastore::EntryRef levels_ref;
    double distance;
    HnswTraversalCandidate(uint32_t nodeid_in, double distance_in) noexcept
      : nodeid(nodeid_in), levels_ref(), distance(distance_in) {}
    HnswTraversalCandidate(uint32_t nodeid_in, vespalib::datastore::EntryRef levels_ref_in, double distance_in) noexcept
      : nodeid(nodeid_in), levels_ref(levels_ref_in), distance(distance_in) {}
    HnswTraversalCandidate(uint32_t nodeid_in, uint32_t docid_in, vespalib::datastore::EntryRef levels_ref_in, double distance_in) noexcept
      : nodeid(nodeid_in), levels_ref(levels_ref_in), distance(distance_in)
    {
        (void) docid_in;
    }
};

/**
 * Represents a neighbor candidate node with its distance to another
 * point in space.
 */
struct HnswCandidate : public HnswTraversalCandidate {
    uint32_t docid;

    HnswCandidate(uint32_t nodeid_in, uint32_t docid_in, vespalib::datastore::EntryRef levels_ref_in, double distance_in) noexcept
        : HnswTraversalCandidate(nodeid_in, docid_in, levels_ref_in, distance_in),
          docid(docid_in)
    {
    }
};

struct GreaterDistance {
    bool operator() (const HnswTraversalCandidate& lhs, const HnswTraversalCandidate& rhs) const {
        return (rhs.distance < lhs.distance);
    }
};

struct LesserDistance {
    bool operator() (const HnswTraversalCandidate& lhs, const HnswTraversalCandidate& rhs) const {
        return (lhs.distance < rhs.distance);
    }
};

using HnswTraversalCandidateVector = std::vector<HnswTraversalCandidate>;

using HnswCandidateVector = std::vector<HnswCandidate>;

/**
 * Priority queue that keeps the candidate node that is nearest a point in space on top.
 */
using NearestPriQ = std::priority_queue<HnswTraversalCandidate, HnswTraversalCandidateVector, GreaterDistance>;

/**
 * Priority queue that keeps the candidate node that is furthest away a point in space on top.
 */
class FurthestPriQ : public std::priority_queue<HnswCandidate, HnswCandidateVector, LesserDistance> {
public:
    const HnswCandidateVector& peek() const { return c; }
};

}

