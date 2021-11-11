// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "hitrank.h"
#include <cstdint>
#include <cstddef>

namespace search {

struct RankedHit {
    RankedHit() noexcept : _docId(0), _rankValue(zero_rank_value) { }
    RankedHit(unsigned int docId, HitRank rank = zero_rank_value) noexcept : _docId(docId), _rankValue(rank) { }
    unsigned int getDocId() const { return _docId; }
    HitRank getRank()       const { return _rankValue; }
//:private
    unsigned int _docId;
    HitRank _rankValue;
};

class RankedHitIterator {
public:
    RankedHitIterator(const RankedHit * h, size_t sz) noexcept : _h(h), _sz(sz), _pos(0) { }
    bool hasNext() const noexcept { return _pos < _sz; }
    uint32_t next() noexcept { return _h[_pos++].getDocId(); }
private:
    const RankedHit *_h;
    const size_t     _sz;
    size_t           _pos;
};

} // namespace search

