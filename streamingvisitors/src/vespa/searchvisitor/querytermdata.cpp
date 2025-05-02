// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "querytermdata.h"

using namespace search::fef;

namespace streaming {

QueryTermDataFactory::~QueryTermDataFactory() = default;

const search::queryeval::IElementGapInspector&
QueryTermDataFactory::get_element_gap_inspector() const noexcept
{
    if (_element_gap_inspector == nullptr) {
        return search::streaming::QueryNodeResultFactory::get_element_gap_inspector();
    } else {
        return *_element_gap_inspector;
    }
}

} // namespace streaming

