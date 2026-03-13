// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/bit_span.h>

namespace search::attribute {

/**
 * Read view for array of bool attributes, returning BitSpan per document.
 */
class IArrayBoolReadView {
public:
    using BitSpan = vespalib::BitSpan;
    virtual ~IArrayBoolReadView() = default;
    virtual BitSpan get_values(uint32_t docid) const = 0;
};

}
