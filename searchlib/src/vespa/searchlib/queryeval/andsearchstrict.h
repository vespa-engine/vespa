// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "andsearchnostrict.h"

namespace search::queryeval {

/**
 * A simple strict implementation of the And search operation.
 **/
template <typename Unpack>
class AndSearchStrict : public AndSearchNoStrict<Unpack>
{
private:
    template<bool doSeekOnly>
    VESPA_DLL_LOCAL void advance(uint32_t failedChildIndexd) __attribute__((noinline));
    using Trinary=vespalib::Trinary;
protected:
    void doSeek(uint32_t docid) override;
    Trinary is_strict() const override { return Trinary::True; }
    SearchIterator::UP andWith(SearchIterator::UP filter, uint32_t estimate) override;
public:
    AndSearchStrict(MultiSearch::Children children, const Unpack & unpacker)
        : AndSearchNoStrict<Unpack>(std::move(children), unpacker)
    {
    }

    void initRange(uint32_t beginid, uint32_t endid) override {
        AndSearchNoStrict<Unpack>::initRange(beginid, endid);
        advance<false>(0);
    }
};

template<typename Unpack>
template<bool doSeekOnly>
void
AndSearchStrict<Unpack>::advance(uint32_t failedChildIndex)
{
    const MultiSearch::Children & children(this->getChildren());
    SearchIterator & firstChild(*children[0]);
    bool foundHit(false);
    if (failedChildIndex != 0) {
        if (doSeekOnly) {
            if (__builtin_expect(children[failedChildIndex]->isAtEnd(), false)) {
                this->setAtEnd();
                return;
            }
            firstChild.doSeek(std::max(firstChild.getDocId() + 1, children[failedChildIndex]->getDocId()));
        } else {
            firstChild.seek(std::max(firstChild.getDocId() + 1, children[failedChildIndex]->getDocId()));
        }
    }
    uint32_t nextId(firstChild.getDocId());
    while (!foundHit && !this->isAtEnd(nextId)) {
        foundHit = true;
        for (uint32_t i(1); foundHit && (i < children.size()); ++i) {
            SearchIterator & child(*children[i]);
            if (!(foundHit = child.seek(nextId))) {
                if (__builtin_expect(!child.isAtEnd(), true)) {
                    firstChild.doSeek(std::max(nextId+1, child.getDocId()));
                    nextId = firstChild.getDocId();
                } else {
                    this->setAtEnd();
                    return;
                }
            }
        }
    }
    this->setDocId(nextId);
}

template<typename Unpack>
void
AndSearchStrict<Unpack>::doSeek(uint32_t docid)
{
    const MultiSearch::Children & children(this->getChildren());
    for (uint32_t i(0); i < children.size(); ++i) {
        children[i]->doSeek(docid);
        if (children[i]->getDocId() != docid) {
            advance<true>(i);
            return;
        }
    }
    this->setDocId(docid);
}

template<typename Unpack>
SearchIterator::UP
AndSearchStrict<Unpack>::andWith(SearchIterator::UP filter, uint32_t estimate_)
{
    filter = this->getChildren()[0]->andWith(std::move(filter), estimate_);
    if (filter) {
        if ((estimate_ < this->estimate()) && (filter->is_strict() == Trinary::True)) {
            this->insert(0, std::move(filter));
        } else {
            filter = this->offerFilterToChildren(std::move(filter), estimate_);
            if (filter) {
                this->insert(1, std::move(filter));
            }
        }
    }
    return filter; // Should always be empty, returning it incase logic changes.
}

}
