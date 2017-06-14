// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 1998-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#pragma once

#include "hitrank.h"
#include <sys/types.h>
#include <stdint.h>

namespace search {

struct RankedHit {
    RankedHit() : _docId(0), _rankValue(0) { }
    RankedHit(unsigned int docId, HitRank rank=0.0) : _docId(docId), _rankValue(rank) { }
    unsigned int getDocId() const { return _docId & 0x7fffffff; }
    bool hasMore()          const { return _docId & 0x80000000; }
    HitRank getRank()       const { return _rankValue; }
//:private
    unsigned int _docId;
    HitRank _rankValue;
};

class RankedHitIterator {
public:
    RankedHitIterator(const RankedHit * h, size_t sz) : _h(h), _sz(sz), _pos(0) { }
    bool hasNext() const { return _pos < _sz; }
    uint32_t next() { return _h[_pos++].getDocId(); }
private:
    const RankedHit *_h;
    const size_t _sz;
    size_t _pos;
};

} // namespace search

