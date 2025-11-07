// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    bool _elementwise;

    void doSeek(uint32_t docid) override;
    void doUnpack(uint32_t docid) override;
    Trinary is_strict() const override { return Trinary::False; }

    /**
     * Create a new AndNot Search with the given children.
     *A AndNot has no strictness assumptions about its children.
     *
     * @param children the search objects we are andnot'ing
     **/
    explicit AndNotSearch(MultiSearch::Children children, bool elementwise);

public:
    ~AndNotSearch() override;
    static std::unique_ptr<SearchIterator> create(ChildrenIterators children, bool strict);
    static std::unique_ptr<SearchIterator> create(ChildrenIterators children, bool elementwise, bool strict);

    std::unique_ptr<BitVector> get_hits(uint32_t begin_id) override;
    void or_hits_into(BitVector &result, uint32_t begin_id) override;
    void get_element_ids(uint32_t docid, std::vector<uint32_t>& element_ids) override;

private:
    bool isAndNot() const override { return true; }
    bool needUnpack(size_t index) const override {
        return index == 0;
    }
};

class AndNotSearchStrictBase : public AndNotSearch
{
protected:
    explicit AndNotSearchStrictBase(Children children, bool elementwise)
        : AndNotSearch(std::move(children), elementwise)
    { }
private:
    Trinary is_strict() const override { return Trinary::True; }
    UP andWith(UP filter, uint32_t estimate) override;
};

}
