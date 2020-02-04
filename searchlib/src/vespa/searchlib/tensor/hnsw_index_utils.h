// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <queue>
#include <vector>

namespace search::tensor {

/**
 * Represents a candidate node with its distance to another point in space.
 */
struct HnswCandidate {
    uint32_t docid;
    double distance;
    HnswCandidate(uint32_t docid_in, double distance_in) : docid(docid_in), distance(distance_in) {}
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

