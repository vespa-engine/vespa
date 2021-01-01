// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/objects/nbostream.h>

namespace vespalib::eval {

/**
 *  Reads a stream of serialized labels.
 *  Reading more labels than available will
 *  throw an exception.
 **/
struct LabelStream {
    nbostream source;
    LabelStream(ConstArrayRef<char> data) : source(data.begin(), data.size()) {}
    vespalib::stringref next_label() {
        size_t str_size = source.getInt1_4Bytes();
        vespalib::stringref label(source.peek(), str_size);
        source.adjustReadPos(str_size);
        return label;
    }
    void reset() { source.rp(0); }
};

/**
 *  Represents an address (set of labels) mapping to a subspace index
 **/
struct LabelBlock {
    static constexpr size_t npos = -1;
    size_t subspace_index;
    ConstArrayRef<vespalib::stringref> address;
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
    std::vector<vespalib::stringref> _current_address;
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
                     ConstArrayRef<char> label_buf,
                     uint32_t num_mapped_dims)
      : _num_subspaces(num_subspaces),
        _labels(label_buf),
        _subspace_index(num_subspaces),
        _current_address(num_mapped_dims)
    {}

    ~LabelBlockStream();
};

} // namespace
