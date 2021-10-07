// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multisearch.h"
#include <vespa/searchlib/attribute/attributeiterators.h>
#include <vespa/searchlib/attribute/singlesmallnumericattribute.h>

namespace search::queryeval {

/**
 * A simple implementation of the AndNot search operation.
 **/
class AndNotSearch : public MultiSearch
{
protected:
    void doSeek(uint32_t docid) override;
    void doUnpack(uint32_t docid) override;
    Trinary is_strict() const override { return Trinary::False; }

    /**
     * Create a new AndNot Search with the given children.
     *A AndNot has no strictness assumptions about its children.
     *
     * @param children the search objects we are andnot'ing
     **/
    AndNotSearch(MultiSearch::Children children) : MultiSearch(std::move(children)) { }

public:
    static std::unique_ptr<SearchIterator> create(ChildrenIterators children, bool strict);

    std::unique_ptr<BitVector> get_hits(uint32_t begin_id) override;
    void or_hits_into(BitVector &result, uint32_t begin_id) override;

private:
    bool isAndNot() const override { return true; }
    bool needUnpack(size_t index) const override {
        return index == 0;
    }
};

class AndNotSearchStrictBase : public AndNotSearch
{
protected:
    AndNotSearchStrictBase(Children children) : AndNotSearch(std::move(children)) { }
private:
    Trinary is_strict() const override { return Trinary::True; }
    UP andWith(UP filter, uint32_t estimate) override;
};

/**
 * This is a specialized andnot iterator you get when you have no andnot's in you query and only get the blacklist blueprint.
 * This one is now constructed at getSearch() phase. However this should be better handled in the AndNotBlueprint.
 */
class OptimizedAndNotForBlackListing : public AndNotSearchStrictBase
{
private:
    // This is the actual iterator that should be produced by the documentmetastore in searchcore, but that
    // will probably be changed later on. An ordinary bitvector could be even better as that would open up for more optimizations.
    //typedef FilterAttributeIteratorT<SingleValueSmallNumericAttribute::SingleSearchContext> BlackListIterator;
    typedef AttributeIteratorT<SingleValueSmallNumericAttribute::SingleSearchContext> BlackListIterator;
public:
    OptimizedAndNotForBlackListing(MultiSearch::Children children);
    static bool isBlackListIterator(const SearchIterator * iterator);

    uint32_t seekFast(uint32_t docid) {
        return internalSeek<true>(docid);
    }
    void initRange(uint32_t beginid, uint32_t endid) override;
private:
    SearchIterator * positive() { return getChildren()[0].get(); }
    BlackListIterator * blackList() { return static_cast<BlackListIterator *>(getChildren()[1].get()); }
    template<bool doSeekOnly>
    uint32_t internalSeek(uint32_t docid) {
        uint32_t curr(docid);
        while (true) {
            if (doSeekOnly) {
                positive()->doSeek(curr);
            } else {
                positive()->seek(curr);
            }
            if ( ! positive()->isAtEnd() ) {
                curr = positive()->getDocId();
                if (! blackList()->seekFast(curr)) {
                    return curr;
                }
                curr++;
            } else {
                return search::endDocId;
            }
        }
    }
    void doSeek(uint32_t docid) override;
    void doUnpack(uint32_t docid) override;
};

}
