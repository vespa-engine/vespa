// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "termwise_search.h"
#include <vespa/vespalib/objects/visit.h>
#include <vespa/searchlib/common/bitvector.h>

namespace search::queryeval {

template <bool IS_STRICT>
struct TermwiseSearch : public SearchIterator {

    SearchIterator::UP search;
    BitVector::UP      result;
    uint32_t           my_beginid;
    uint32_t           my_first_hit;

    bool same_range(uint32_t beginid, uint32_t endid) const {
        return ((beginid == my_beginid) && endid == getEndId());
    }

    TermwiseSearch(SearchIterator::UP search_in)
        : search(std::move(search_in)), result(), my_beginid(0), my_first_hit(0) {}

    Trinary is_strict() const override { return IS_STRICT ? Trinary::True : Trinary::False; }
    void initRange(uint32_t beginid, uint32_t endid) override {
        if (!same_range(beginid, endid)) {
            my_beginid = beginid;
            SearchIterator::initRange(beginid, endid);
            search->initRange(beginid, endid);
            my_first_hit = std::max(getDocId(), search->getDocId());
            result = search->get_hits(beginid);
        }
        setDocId(my_first_hit);
    }
    void doSeek(uint32_t docid) override {
        if (__builtin_expect(isAtEnd(docid), false)) {
            setAtEnd();
        } else if (IS_STRICT) {
            uint32_t nextid = result->getNextTrueBit(docid);
            if (__builtin_expect(isAtEnd(nextid), false)) {
                setAtEnd();
            } else {
                setDocId(nextid);
            }
        } else if (result->testBit(docid)) {
            setDocId(docid);
        }
    }
    void doUnpack(uint32_t) override {}
    void visitMembers(vespalib::ObjectVisitor &visitor) const override {
        visit(visitor, "search", *search);
        visit(visitor, "strict", IS_STRICT);
    }
};

SearchIterator::UP
make_termwise(SearchIterator::UP search, bool strict)
{
    if (strict) {
        return SearchIterator::UP(new TermwiseSearch<true>(std::move(search)));
    } else {
        return SearchIterator::UP(new TermwiseSearch<false>(std::move(search)));        
    }
}

}
