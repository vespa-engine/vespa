// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "booleanmatchiteratorwrapper.h"
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/vespalib/objects/visit.hpp>

namespace search::queryeval {

void
BooleanMatchIteratorWrapper::doSeek(uint32_t docid)
{
    _search->seek(docid);          // use outer seek for most robustness
    setDocId(_search->getDocId()); // propagate current iterator docid
}

void
BooleanMatchIteratorWrapper::doUnpack(uint32_t docid)
{
    if (_tfmdp != 0) {           // handle not having a match data (unranked, or multiple fields)
        _tfmdp->reset(docid);    // unpack ensures that docid is a hit
    }
}

BooleanMatchIteratorWrapper::BooleanMatchIteratorWrapper(
        SearchIterator::UP search,
        const fef::TermFieldMatchDataArray &matchData)
    : _search(std::move(search)),
      _tfmdp(0)
{
    if (matchData.size() == 1) {
        _tfmdp = matchData[0];
    }
}

void
BooleanMatchIteratorWrapper::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    visit(visitor, "search", _search);
    // _match not visited
}

}
