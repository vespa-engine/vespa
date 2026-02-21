// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "blueprint.h"

namespace search::attribute { class ArrayBoolAttribute; };

namespace search::queryeval {

/**
 * Blueprint for Checking array of bool at specific indices.
 *
 * Replaces SameElementBlueprint if it is used for indexing into an array of bool.
 */
class ArrayBoolBlueprint : public SimpleLeafBlueprint {
    using ArrayBoolAttribute = search::attribute::ArrayBoolAttribute;

    const ArrayBoolAttribute& _attr;
    const std::vector<uint32_t> _element_filter;
    bool _want_true;
    bool _strict;

public:
    ArrayBoolBlueprint(const ArrayBoolAttribute& attr, const std::vector<uint32_t>& element_filter, bool want_true, bool strict);

    search::queryeval::FlowStats calculate_flow_stats(uint32_t docid_limit) const override;

    std::unique_ptr<SearchIterator> createLeafSearch(const search::fef::TermFieldMatchDataArray& tfmda) const override;
    std::unique_ptr<SearchIterator> createFilterSearchImpl(FilterConstraint constraint) const override;
};

}
