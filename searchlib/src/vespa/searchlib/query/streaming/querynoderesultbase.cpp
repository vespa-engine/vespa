// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "querynoderesultbase.h"
#include <vespa/searchlib/queryeval/i_element_gap_inspector.h>
#include <ostream>

using search::queryeval::IElementGapInspector;

namespace search::streaming {

namespace {

class NoElementGapInspector : public IElementGapInspector {
public:
    NoElementGapInspector();
    ~NoElementGapInspector() override;
    std::optional<uint32_t> get_element_gap(uint32_t field_id) const noexcept override;
};

NoElementGapInspector::NoElementGapInspector() = default;

NoElementGapInspector::~NoElementGapInspector() = default;

std::optional<uint32_t>
NoElementGapInspector::get_element_gap(uint32_t) const noexcept
{
    return std::nullopt;
}

NoElementGapInspector no_element_gap_inspector;

}

const search::queryeval::IElementGapInspector&
QueryNodeResultFactory::get_element_gap_inspector() const noexcept {
    return no_element_gap_inspector;
}

}
