// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simpleresult.h"
#include <cassert>
#include <ostream>

namespace search::queryeval {

SimpleResult &
SimpleResult::addHit(uint32_t docid)
{
    _hits.push_back(docid);
    return *this;
}

void
SimpleResult::clear()
{
    std::vector<uint32_t> tmp;
    tmp.swap(_hits);
}

SimpleResult &
SimpleResult::search(SearchIterator &sb, uint32_t docIdLimit)
{
    clear();
    uint32_t docid = 1;
    sb.initRange(docid, docIdLimit);
    while (!sb.isAtEnd(docid)) {
        if (sb.seek(docid)) {
            assert(sb.getDocId() == docid);
            sb.unpack(docid);
            _hits.push_back(docid);
        }
        docid = std::max(docid + 1, sb.getDocId());
    }
    return *this;
}

bool
SimpleResult::contains(const SimpleResult& subset) const
{
    auto hits_itr = _hits.begin();
    for (uint32_t i = 0; i < subset.getHitCount(); ++i) {
        uint32_t subset_hit = subset.getHit(i);
        while (hits_itr != _hits.end() && *hits_itr < subset_hit) {
            ++hits_itr;
        }
        if (hits_itr == _hits.end() || *hits_itr > subset_hit) {
            return false;
        }
    }
    return true;
}

std::ostream &
operator << (std::ostream &out, const SimpleResult &result)
{
    if (result.getHitCount() == 0) {
        out << std::endl << "empty" << std::endl;
    } else {
        out << std::endl;
        for (uint32_t i = 0; i < result.getHitCount(); ++i) {
            out << "{" << result.getHit(i) << "}" << std::endl;
        }
    }
    return out;
}

}
