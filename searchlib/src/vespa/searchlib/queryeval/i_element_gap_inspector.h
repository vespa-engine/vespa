// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/element_gap.h>

namespace search::queryeval {

/*
 * Interface class for getting element gap (gap between positions in adjacent elements in multi-value fields.
 *
 * Normally, the information is retrieved using IIndexEnvironment::getField(). Consider renaming this interface
 * and extending it if more information from FieldInfo is needed.
 */
class IElementGapInspector {
public:
    virtual search::fef::ElementGap get_element_gap(uint32_t field_id) const noexcept = 0;
    virtual ~IElementGapInspector() = default;
};

}
