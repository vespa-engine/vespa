// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "blueprint.h"
#include "flow.h"

#include <cstdint>
#include <memory>
#include <vector>

namespace search::attribute {
class ArrayBoolAttribute;
};

namespace search::queryeval {

class FieldSpecBase;

/**
 * Blueprint for checking ArrayBoolAttribute at one or more indices.
 * Intended as replacement for SameElementBlueprint if it is only used for indexing into an array of bool.
 * Creates an ArrayBoolSearch as opposed to the combination of a SameElementSearch and an ArrayBoolSearch
 * that the SameElementBlueprint would create in the same situation.
 */
class ArrayBoolBlueprint : public SimpleLeafBlueprint {
    using ArrayBoolAttribute = search::attribute::ArrayBoolAttribute;

    const ArrayBoolAttribute&   _attr;
    const std::vector<uint32_t> _element_filter;
    bool                        _want_true;

public:
    ArrayBoolBlueprint(FieldSpecBase field, const ArrayBoolAttribute& attr,
                       const std::vector<uint32_t>& element_filter, bool want_true);

    search::queryeval::FlowStats calculate_flow_stats(uint32_t docid_limit) const override;

    std::unique_ptr<SearchIterator>
    createLeafSearch(const search::fef::TermFieldMatchDataArray& tfmda) const override;
    std::unique_ptr<SearchIterator> createFilterSearchImpl(FilterConstraint constraint) const override;

    void visitMembers(vespalib::ObjectVisitor& visitor) const override;
};

} // namespace search::queryeval
