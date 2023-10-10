// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributeguard.h"
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vector>

namespace search {

namespace attribute { class ISearchContext; }

class AttributeWeightedSetBlueprint : public queryeval::ComplexLeafBlueprint
{
private:
    using ISearchContext = attribute::ISearchContext;
    using IAttributeVector = attribute::IAttributeVector;
    size_t                         _numDocs;
    size_t                         _estHits;
    std::vector<int32_t>           _weights;
    const IAttributeVector       & _attr;
    std::vector<ISearchContext*>   _contexts;

public:
    AttributeWeightedSetBlueprint(const AttributeWeightedSetBlueprint &) = delete;
    AttributeWeightedSetBlueprint &operator=(const AttributeWeightedSetBlueprint &) = delete;
    AttributeWeightedSetBlueprint(const queryeval::FieldSpec &field, const IAttributeVector & attr);
    ~AttributeWeightedSetBlueprint();
    void addToken(std::unique_ptr<ISearchContext> context, int32_t weight);
    queryeval::SearchIterator::UP createLeafSearch(const fef::TermFieldMatchDataArray &tfmda, bool strict) const override;
    queryeval::SearchIterator::UP createFilterSearch(bool strict, FilterConstraint constraint) const override;
    void fetchPostings(const queryeval::ExecuteInfo &execInfo) override;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
};

} // namespace search

