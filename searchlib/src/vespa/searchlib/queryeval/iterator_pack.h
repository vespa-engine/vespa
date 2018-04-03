// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchiterator.h"
#include <vespa/searchlib/fef/termfieldmatchdata.h>

namespace search::fef { class MatchData; }

namespace search::queryeval {

class SearchIteratorPack
{
private:
    using MatchDataUP = std::unique_ptr<fef::MatchData>;
    std::vector<SearchIterator::UP>        _children;
    std::vector<fef::TermFieldMatchData*>  _childMatch;
    MatchDataUP                            _md;

public:
    SearchIteratorPack();
    ~SearchIteratorPack();
    SearchIteratorPack(SearchIteratorPack &&rhs);
    SearchIteratorPack &operator=(SearchIteratorPack &&rhs);

    SearchIteratorPack(const std::vector<SearchIterator*> &children,
                       const std::vector<fef::TermFieldMatchData*> &childMatch,
                       MatchDataUP md);

    SearchIteratorPack(const std::vector<SearchIterator*> &children, MatchDataUP md);

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
    std::unique_ptr<BitVector> get_hits(uint32_t begin_id, uint32_t end_id) const;
    void or_hits_into(BitVector &result, uint32_t begin_id) const;
};

}

