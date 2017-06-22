// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simpleresult.h"
#include <cassert>
#include <ostream>

namespace search {
namespace queryeval {

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

void
SimpleResult::search(SearchIterator &sb)
{
    clear();
    // assume strict toplevel search object located at start
    sb.initFullRange();
    for (sb.seek(1); !sb.isAtEnd(); sb.seek(sb.getDocId() + 1)) {
        sb.unpack(sb.getDocId());
        _hits.push_back(sb.getDocId());
    }
}

void
SimpleResult::searchStrict(SearchIterator &sb, uint32_t docIdLimit)
{
    clear();
    // assume strict toplevel search object located at start
    sb.initRange(1, docIdLimit);
    for (sb.seek(1); !sb.isAtEnd(); sb.seek(sb.getDocId() + 1)) {
        sb.unpack(sb.getDocId());
        _hits.push_back(sb.getDocId());
    }
}

void
SimpleResult::search(SearchIterator &sb, uint32_t docIdLimit)
{
    clear();
    // assume non-strict toplevel search object
    sb.initRange(1, docIdLimit);
    for (uint32_t docId = 1; !sb.isAtEnd(docId); ++docId) {
        if (sb.seek(docId)) {
            assert(docId == sb.getDocId());
            sb.unpack(docId);
            _hits.push_back(docId);
        }
    }
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

} // namespace queryeval
} // namespace search
