// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/queryeval/i_element_gap_inspector.h>

namespace search::queryeval::test {

/*
 * Mock class for getting element gap (gap between positions in adjacent elements in multi-value fields.
 */
class MockElementGapInspector : public IElementGapInspector {
    search::fef::ElementGap _element_gap;
public:
    MockElementGapInspector(search::fef::ElementGap element_gap);
    ~MockElementGapInspector() override;
    search::fef::ElementGap get_element_gap(uint32_t field_id) const noexcept override;
};

}
