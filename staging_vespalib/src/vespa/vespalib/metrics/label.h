// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib {
namespace metrics {

using LabelValue = vespalib::string;

class Label {
    const size_t _coord_idx;
public:
    size_t id() const { return _coord_idx; }
    Label(size_t id) : _coord_idx(id) {}
};

} // namespace vespalib::metrics
} // namespace vespalib
