// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simplesearch.h"
#include <vespa/vespalib/objects/visit.h>

namespace search::queryeval {

void
SimpleSearch::doSeek(uint32_t docid)
{
    while (_index < _result.getHitCount() && _result.getHit(_index) < docid) {
        ++_index;
    }
    auto candidate = (_index < _result.getHitCount())
        ? _result.getHit(_index) : search::endDocId;
    if ((candidate == docid) || _strict) {
        setDocId(candidate);
    }
}

void
SimpleSearch::doUnpack(uint32_t docid)
{
    (void) docid;
}

SimpleSearch::SimpleSearch(const SimpleResult &result, bool strict)
  : _tag("<null>"),
    _result(result),
    _index(0),
    _strict(strict)
{
}

void
SimpleSearch::initRange(uint32_t begin_id, uint32_t end_id)
{
    SearchIterator::initRange(begin_id, end_id);
    _index = 0;
}

void
SimpleSearch::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    visit(visitor, "tag", _tag);
}

SimpleSearch::~SimpleSearch() = default;

}
