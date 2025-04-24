// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mock_element_gap_inspector.h"

namespace search::queryeval::test {

MockElementGapInspector::MockElementGapInspector(std::optional<uint32_t> element_gap)
    : IElementGapInspector(),
      _element_gap(element_gap)
{
}

MockElementGapInspector::~MockElementGapInspector() = default;

std::optional<uint32_t>
MockElementGapInspector::get_element_gap(uint32_t) const noexcept
{
    return _element_gap;
}

}
