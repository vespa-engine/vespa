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
    if (_index == _result.getHitCount()) {
        setAtEnd();
        return;
    }
    setDocId(_result.getHit(_index));
}

void
SimpleSearch::doUnpack(uint32_t docid)
{
    (void) docid;
}

SimpleSearch::SimpleSearch(const SimpleResult &result)
    : _tag("<null>"),
      _result(result),
      _index(0)
{
}

void
SimpleSearch::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    visit(visitor, "tag", _tag);
}

SimpleSearch::~SimpleSearch() = default;

}
