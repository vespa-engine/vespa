// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <cassert>

namespace vespalib::eval {

/**
 *  Reads a stream of serialized labels.
 *  Reading more labels than available will trigger an assert.
 **/
struct LabelStream {
    const std::vector<string_id> &source;
    size_t pos;
    LabelStream(const std::vector<string_id> &data) : source(data), pos(0) {}
    string_id next_label() {
        assert(pos < source.size());
        return source[pos++];
    }
    void reset() { pos = 0; }
};

/**
 *  Represents an address (set of labels) mapping to a subspace index
 **/
struct LabelBlock {
    static constexpr size_t npos = -1;
    size_t subspace_index;
    ConstArrayRef<string_id> address;
    operator bool() const { return subspace_index != npos; }
};

/**
 * Utility for reading a buffer with serialized labels
 * as a stream of LabelBlock objects.
 **/
class LabelBlockStream {
private:
    size_t _num_subspaces;
    LabelStream _labels;
    size_t _subspace_index;
    std::vector<string_id> _current_address;
public:
    LabelBlock next_block() {
        if (_subspace_index < _num_subspaces) {
            for (auto & label : _current_address) {
                label = _labels.next_label();
            }
            return LabelBlock{_subspace_index++, _current_address};
        } else {
            return LabelBlock{LabelBlock::npos, {}};
        }
    }

    void reset() {
        _subspace_index = 0;
        _labels.reset();
    }

    LabelBlockStream(uint32_t num_subspaces,
                     const std::vector<string_id> &labels,
                     uint32_t num_mapped_dims)
      : _num_subspaces(num_subspaces),
        _labels(labels),
        _subspace_index(num_subspaces),
        _current_address(num_mapped_dims)
    {}

    ~LabelBlockStream();
};

} // namespace
