// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchiterator.h"
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/fef/matchdata.h>

namespace search {
namespace queryeval {

class SearchIteratorPack
{
private:
    std::vector<SearchIterator::UP>        _children;
    std::vector<fef::TermFieldMatchData*>  _childMatch;
    fef::MatchData::UP                     _md;

public:
    SearchIteratorPack() : _children(), _childMatch(), _md() {}
    SearchIteratorPack(SearchIteratorPack &&rhs)
        : _children(std::move(rhs._children)),
          _childMatch(std::move(rhs._childMatch)),
          _md(std::move(rhs._md)) {}

    SearchIteratorPack &operator=(SearchIteratorPack &&rhs) {
        _children = std::move(rhs._children);
        _childMatch = std::move(rhs._childMatch);
        _md = std::move(rhs._md);
        return *this;
    }

    SearchIteratorPack(const std::vector<SearchIterator*> &children,
                       const std::vector<fef::TermFieldMatchData*> &childMatch,
                       fef::MatchData::UP md)
        : _children(),
          _childMatch(childMatch),
          _md(std::move(md))
    {
        _children.reserve(children.size());
        for (auto child: children) {
            _children.emplace_back(child);
        }
        assert((_children.size() == _childMatch.size()) ||
               (_childMatch.empty() && (_md.get() == nullptr)));
    }

    explicit SearchIteratorPack(const std::vector<SearchIterator*> &children)
        : SearchIteratorPack(children,
                             std::vector<fef::TermFieldMatchData*>(),
                             fef::MatchData::UP()) {}

    uint32_t get_docid(uint32_t ref) const {
        return _children[ref]->getDocId();
    }

    uint32_t seek(uint32_t ref, uint32_t docid) {
        _children[ref]->seek(docid);
        return _children[ref]->getDocId();
    }

    int32_t get_weight(uint32_t ref, uint32_t docid) {
        _children[ref]->doUnpack(docid);
        return _childMatch[ref]->getWeight();
    }

    void unpack(uint32_t ref, uint32_t docid) {
        _children[ref]->doUnpack(docid);
    }

    size_t size() const {
        return _children.size();
    }
    void initRange(uint32_t begin, uint32_t end) {
        for (auto & child: _children) {
            child->initRange(begin, end);
        }
    }
    std::unique_ptr<BitVector> get_hits(uint32_t begin_id, uint32_t end_id);
    void or_hits_into(BitVector &result, uint32_t begin_id) const;
};

} // namespace queryevel
} // namespace search

