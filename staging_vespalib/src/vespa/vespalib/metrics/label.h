// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include "handle.h"

namespace vespalib::metrics {

using LabelValue = vespalib::string;

struct LabelTag {};

/**
 * Opaque handle representing an unique label value.
 **/
struct Label : Handle<LabelTag>
{
    explicit Label(size_t id) : Handle(id) {}
    static Label from_value(const vespalib::string& value);
    const vespalib::string& as_value() const;
};

} // namespace vespalib::metrics
