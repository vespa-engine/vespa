// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "termwise_search.h"
#include <vespa/vespalib/objects/visit.h>
#include <vespa/searchlib/common/bitvector.h>

namespace search {
namespace queryeval {

template <bool IS_STRICT>
struct TermwiseSearch : public SearchIterator {

    SearchIterator::UP search;
    BitVector::UP  result;

    TermwiseSearch(SearchIterator::UP search_in)
        : search(std::move(search_in)), result() {}

    Trinary is_strict() const override { return IS_STRICT ? Trinary::True : Trinary::False; }
    void initRange(uint32_t beginid, uint32_t endid) override {
        SearchIterator::initRange(beginid, endid);
        search->initRange(beginid, endid);
        setDocId(std::max(getDocId(), search->getDocId()));
        result = search->get_hits(beginid);
    }
    void resetRange() override {
        SearchIterator::resetRange();
        search->resetRange();
        result.reset();
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
    void visitMembers(vespalib::ObjectVisitor &visitor) const {
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

} // namespace queryeval
} // namespace search
