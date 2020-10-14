// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/memory_usage_stuff.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/arrayref.h>

namespace vespalib::eval::packed_mixed_tensor {

/**
 *  Stores labels for sparse (mapped) tensor dimensions,
 *  where each unique label value is stored only once,
 *  and the values are sorted.  References data that
 *  must be constant and owned by some object with
 *  enclosing lifetime.
 **/
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

    vespalib::stringref get_label(uint32_t index) const;

    MemoryUsage estimate_extra_memory_usage() const;
private:
    const ConstArrayRef<uint32_t> _offsets;
    const ConstArrayRef<char> _label_store;

    void validate_labels(uint32_t num_labels);
};

} // namespace
