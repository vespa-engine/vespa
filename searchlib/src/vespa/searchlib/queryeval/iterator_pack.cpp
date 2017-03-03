// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "iterator_pack.h"
#include <vespa/searchlib/common/bitvector.h>

namespace search {
namespace queryeval {

SearchIteratorPack::SearchIteratorPack()
    : _children(),
      _childMatch(),
      _md()
{}

SearchIteratorPack::~SearchIteratorPack() { }

SearchIteratorPack::SearchIteratorPack(SearchIteratorPack &&rhs)
    : _children(std::move(rhs._children)),
      _childMatch(std::move(rhs._childMatch)),
      _md(std::move(rhs._md))
{}

SearchIteratorPack &
SearchIteratorPack::operator=(SearchIteratorPack &&rhs) {
    _children = std::move(rhs._children);
    _childMatch = std::move(rhs._childMatch);
    _md = std::move(rhs._md);
    return *this;
}

SearchIteratorPack::SearchIteratorPack(const std::vector<SearchIterator*> &children,
                                       const std::vector<fef::TermFieldMatchData*> &childMatch,
                                       fef::MatchData::UP md)
    : _children(),
      _childMatch(childMatch),
      _md(std::move(md))
{
    _children.reserve(children.size());
    for (auto child : children) {
        _children.emplace_back(child);
    }
    assert((_children.size() == _childMatch.size()) ||
           (_childMatch.empty() && (_md.get() == nullptr)));
}

SearchIteratorPack::SearchIteratorPack(const std::vector<SearchIterator*> &children)
    : SearchIteratorPack(children, std::vector<fef::TermFieldMatchData*>(), fef::MatchData::UP())
{ }

std::unique_ptr<BitVector>
SearchIteratorPack::get_hits(uint32_t begin_id, uint32_t end_id) {

    BitVector::UP result = SearchIterator::orChildren(_children, begin_id);
    if (! result ) {
        result = BitVector::create(begin_id, end_id);
    }
    return result;
}

void
SearchIteratorPack::or_hits_into(BitVector &result, uint32_t begin_id) {
    BitVector::UP dirty(&result);
    dirty = SearchIterator::orChildren(std::move(dirty), _children, begin_id);
    dirty.release(); // Yes I know, dirty...
}


}
} // namespace search

