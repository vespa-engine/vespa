// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>

namespace search::tensor {

/**
 * Represents a snapshot of a graph node with all its levels and links.
 * Should only be used by unit tests.
 */
class HnswTestNode {
public:
    using LinkArray = std::vector<uint32_t>;
    using LevelArray = std::vector<LinkArray>;

private:
    LevelArray _levels;

public:
    HnswTestNode() : _levels() {}
    HnswTestNode(const LinkArray& level_0) : _levels() { _levels.push_back(level_0); }
    HnswTestNode(const LevelArray& levels_in) : _levels(levels_in) {}
    bool empty() const { return _levels.empty(); }
    size_t size() const { return _levels.size(); }
    const LevelArray& levels() const { return _levels; }
    const LinkArray& level(size_t idx) const { return _levels[idx]; }
    bool operator==(const HnswTestNode& rhs) {
        return _levels == rhs._levels;
    }
};

}

