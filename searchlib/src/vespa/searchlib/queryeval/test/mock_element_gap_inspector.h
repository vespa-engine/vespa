// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/queryeval/i_element_gap_inspector.h>

namespace search::queryeval::test {

/*
 * Mock class for getting element gap (gap between positions in adjacent elements in multi-value fields.
 */
class MockElementGapInspector : public IElementGapInspector {
    std::optional<uint32_t> _element_gap;
public:
    MockElementGapInspector(std::optional<uint32_t> element_gap);
    ~MockElementGapInspector() override;
    std::optional<uint32_t> get_element_gap(uint32_t field_id) const noexcept override;
};

}
