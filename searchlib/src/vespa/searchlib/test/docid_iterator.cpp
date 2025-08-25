// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docid_iterator.h"

namespace search::test {
DocIdIterator::DocIdIterator(const std::vector<uint32_t>& docIds, bool strict)
    : search::queryeval::SearchIterator(),
      _strict(strict),
      _currIndex(0),
      _docIds(docIds)
{
}

DocIdIterator::~DocIdIterator() = default;

void
DocIdIterator::initRange(uint32_t beginId, uint32_t endId)
{
    SearchIterator::initRange(beginId, endId);
    _currIndex = 0;
    if (_strict) {
        doSeek(beginId);
    }
}

void
DocIdIterator::doSeek(uint32_t docId)
{
    while ((_currIndex < _docIds.size()) && (_docIds[_currIndex] < docId)) {
        _currIndex++;
    }
    if ((_currIndex < _docIds.size()) && (_docIds[_currIndex] < getEndId())) {
        if (_docIds[_currIndex] == docId || _strict) {
            setDocId(_docIds[_currIndex]);
        }
    } else {
        setAtEnd();
    }
}

void
DocIdIterator::doUnpack(uint32_t docid)
{
    (void) docid;
}

vespalib::Trinary
DocIdIterator::is_strict() const
{
    return _strict ? vespalib::Trinary::True : vespalib::Trinary::False;
}

}
