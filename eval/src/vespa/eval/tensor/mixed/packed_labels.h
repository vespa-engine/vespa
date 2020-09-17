// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/arrayref.h>

namespace vespalib::eval {

class PackedLabels {
public:
    PackedLabels(uint32_t num_labels,
                 ConstArrayRef<uint32_t> offsets,
                 ConstArrayRef<char> label_store)
      : _offsets(offsets),
        _label_store(label_store)
    {
        validate_labels(num_labels);
    }

    uint32_t num_labels() const { return _offsets.size() - 1; }

    // returns -1 if the given label value cannot be found
    int32_t find_label(vespalib::stringref value) const;

    vespalib::stringref label_value(uint32_t index) const;

private:
    const ConstArrayRef<uint32_t> _offsets;
    const ConstArrayRef<char> _label_store;

    void validate_labels(uint32_t num_labels);
    const char *get_label_start(uint32_t index) const;
    uint32_t get_label_size(uint32_t index) const;
};

} // namespace
