// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>

namespace search::queryeval {

/**
 * Interface for getting global information stored in underlying posting list
 * used by a search iterator.
 *
 * Subclasses of this interface will expose different information that can be
 * used during evaluation.
 */
struct PostingInfo {
    virtual ~PostingInfo() { }
};


/**
 * Class for getting the min and max weights of a posting list.
 *
 * Such posting lists store a weight with each doc id and maintain the min and
 * max weights among the whole posting list.
 */
class MinMaxPostingInfo : public PostingInfo {
private:
    int32_t _minWeight;
    int32_t _maxWeight;

public:
    MinMaxPostingInfo(int32_t minWeight, int32_t maxWeight) noexcept
        : PostingInfo(),
          _minWeight(minWeight),
          _maxWeight(maxWeight)
    {}
    ~MinMaxPostingInfo() override;
    int32_t getMinWeight() const { return _minWeight; }
    int32_t getMaxWeight() const { return _maxWeight; }
};

}
