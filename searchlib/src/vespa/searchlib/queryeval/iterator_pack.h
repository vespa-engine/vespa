// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    using ref_t = uint16_t;
    SearchIteratorPack();
    ~SearchIteratorPack();
    SearchIteratorPack(SearchIteratorPack &&rhs) noexcept;
    SearchIteratorPack &operator=(SearchIteratorPack &&rhs) noexcept;

    // TODO: use MultiSearch::Children to pass ownership
    SearchIteratorPack(const std::vector<SearchIterator*> &children,
                       const std::vector<fef::TermFieldMatchData*> &childMatch,
                       MatchDataUP md);

    // TODO: use MultiSearch::Children to pass ownership
    SearchIteratorPack(const std::vector<SearchIterator*> &children, MatchDataUP md);

    uint32_t get_docid(ref_t ref) const {
        return _children[ref]->getDocId();
    }

    uint32_t seek(ref_t ref, uint32_t docid) {
        _children[ref]->seek(docid);
        return _children[ref]->getDocId();
    }

    int32_t get_weight(ref_t ref, uint32_t docid) {
        _children[ref]->doUnpack(docid);
        return _childMatch[ref]->getWeight();
    }

    void unpack(ref_t ref, uint32_t docid) {
        _children[ref]->doUnpack(docid);
    }

    ref_t size() const { return _children.size(); }
    void initRange(uint32_t begin, uint32_t end) {
        for (auto & child: _children) {
            child->initRange(begin, end);
        }
    }
    std::unique_ptr<BitVector> get_hits(uint32_t begin_id, uint32_t end_id) const;
    void or_hits_into(BitVector &result, uint32_t begin_id) const;
};

}

