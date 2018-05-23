// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "elementiterator.h"
#include <vespa/searchcommon/attribute/i_search_context.h>

namespace search::attribute {

void
ElementIterator::doSeek(uint32_t docid) {
    _search->doSeek(docid);
}

void
ElementIterator::doUnpack(uint32_t docid) {
    (void) docid;
}

vespalib::Trinary
ElementIterator::is_strict() const {
    return _search->is_strict();
}

void
ElementIterator::initRange(uint32_t beginid, uint32_t endid) {
    _search->initRange(beginid, endid);
    SearchIterator::initRange(_search->getDocId()+1, _search->getEndId());
}

ElementIterator::ElementIterator(SearchIterator::UP search, ISearchContext & sc)
    : _search(std::move(search)),
      _searchContext(sc)
{
}

ElementIterator::~ElementIterator() = default;

}
