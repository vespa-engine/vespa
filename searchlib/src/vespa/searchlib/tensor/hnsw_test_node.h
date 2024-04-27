// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
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
    HnswTestNode() noexcept;
    HnswTestNode(const HnswTestNode &) = delete;
    HnswTestNode & operator=(const HnswTestNode &) = delete;
    HnswTestNode(HnswTestNode &&) noexcept = default;
    ~HnswTestNode();
    HnswTestNode(LinkArray&& level_0) : _levels() { _levels.push_back(std::move(level_0)); }
    HnswTestNode(LevelArray&& levels_in) : _levels(std::move(levels_in)) {}
    bool empty() const noexcept { return _levels.empty(); }
    size_t size() const noexcept { return _levels.size(); }
    const LevelArray& levels() const noexcept { return _levels; }
    const LinkArray& level(size_t idx) const noexcept { return _levels[idx]; }
    bool operator==(const HnswTestNode& rhs) noexcept  {
        return _levels == rhs._levels;
    }
};

}

