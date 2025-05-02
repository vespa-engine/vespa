// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mock_element_gap_inspector.h"

using search::fef::ElementGap;

namespace search::queryeval::test {

MockElementGapInspector::MockElementGapInspector(ElementGap element_gap)
    : IElementGapInspector(),
      _element_gap(element_gap)
{
}

MockElementGapInspector::~MockElementGapInspector() = default;

ElementGap
MockElementGapInspector::get_element_gap(uint32_t) const noexcept
{
    return _element_gap;
}

}
