// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "iterator_pack.h"
#include "termwise_helper.h"
#include <vespa/searchlib/fef/matchdata.h>

namespace search::queryeval {

SearchIteratorPack::~SearchIteratorPack() { }

SearchIteratorPack::SearchIteratorPack() : _children(), _childMatch(), _md() {}

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
                                       MatchDataUP md)
    : _children(),
      _childMatch(childMatch),
      _md(std::move(md))
{
    _children.reserve(children.size());
    for (auto child: children) {
        _children.emplace_back(child);
    }
    assert((_children.size() == _childMatch.size()) || _childMatch.empty());
}

SearchIteratorPack::SearchIteratorPack(const std::vector<SearchIterator*> &children, MatchDataUP md)
    : SearchIteratorPack(children, std::vector<fef::TermFieldMatchData*>(), MatchDataUP(std::move(md)))
{ }

std::unique_ptr<BitVector>
SearchIteratorPack::get_hits(uint32_t begin_id, uint32_t end_id) const {

    BitVector::UP result = TermwiseHelper::orChildren(_children.begin(), _children.end(), begin_id);
    if (! result ) {
        result = BitVector::create(begin_id, end_id);
    }
    return result;
}

void
SearchIteratorPack::or_hits_into(BitVector &result, uint32_t begin_id) const {
    TermwiseHelper::orChildren(result, _children.begin(), _children.end(), begin_id);
}


}
