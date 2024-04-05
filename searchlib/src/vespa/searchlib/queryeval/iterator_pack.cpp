// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "iterator_pack.h"
#include "termwise_helper.h"
#include <vespa/searchlib/fef/matchdata.h>
#include <cassert>
#include <limits>

namespace search::queryeval {

template <typename RefType>
SearchIteratorPackT<RefType>::~SearchIteratorPackT() = default;

template <typename RefType>
SearchIteratorPackT<RefType>::SearchIteratorPackT() = default;

template <typename RefType>
SearchIteratorPackT<RefType>::SearchIteratorPackT(SearchIteratorPackT<RefType> &&rhs) noexcept = default;

template <typename RefType>
SearchIteratorPackT<RefType> &
SearchIteratorPackT<RefType>::operator=(SearchIteratorPackT<RefType> &&rhs) noexcept = default;

template <typename RefType>
SearchIteratorPackT<RefType>::SearchIteratorPackT(const std::vector<SearchIterator*> &children,
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
    assert(_children.size() <= std::numeric_limits<ref_t>::max());
}

template <typename RefType>
SearchIteratorPackT<RefType>::SearchIteratorPackT(const std::vector<SearchIterator*> &children, MatchDataUP md)
    : SearchIteratorPackT(children, std::vector<fef::TermFieldMatchData*>(), MatchDataUP(std::move(md)))
{ }

template <typename RefType>
bool
SearchIteratorPackT<RefType>::can_handle_iterators(size_t num_iterators)
{
    return num_iterators <= std::numeric_limits<ref_t>::max();
}

template <typename RefType>
std::unique_ptr<BitVector>
SearchIteratorPackT<RefType>::get_hits(uint32_t begin_id, uint32_t end_id) const {
    BitVector::UP result = TermwiseHelper::orChildren(_children.begin(), _children.end(), begin_id);
    if (! result ) {
        result = BitVector::create(begin_id, end_id);
    }
    return result;
}

template <typename RefType>
void
SearchIteratorPackT<RefType>::or_hits_into(BitVector &result, uint32_t begin_id) const {
    TermwiseHelper::orChildren(result, _children.begin(), _children.end(), begin_id);
}

template class SearchIteratorPackT<uint16_t>;
template class SearchIteratorPackT<uint32_t>;

}
