// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "streamed_value.h"
#include <vespa/vespalib/objects/nbostream.h>

namespace vespalib::eval {

 /**
  *  Builder for StreamedValue objects.
  **/
template <typename T>
class StreamedValueBuilder : public ValueBuilder<T>
{
private:
    ValueType _type;
    size_t _dsss;
    std::vector<T> _cells;
    size_t _num_subspaces;
    nbostream _labels;
public:
    StreamedValueBuilder(const ValueType &type,
                         size_t num_mapped_in,
                         size_t subspace_size_in,
                         size_t expected_subspaces)
      : _type(type),
        _dsss(subspace_size_in),
        _cells(),
        _num_subspaces(0),
        _labels()
    {
        _cells.reserve(subspace_size_in * expected_subspaces);
        // assume small sized label strings:
        _labels.reserve(num_mapped_in * expected_subspaces * 3);
    };

    ~StreamedValueBuilder();

    ArrayRef<T> add_subspace(ConstArrayRef<vespalib::stringref> addr) override {
        for (auto label : addr) {
            _labels.writeSmallString(label);
        }
        size_t old_sz = _cells.size();
        _cells.resize(old_sz + _dsss);
        _num_subspaces++;
        return ArrayRef<T>(&_cells[old_sz], _dsss);
    }

    std::unique_ptr<Value> build(std::unique_ptr<ValueBuilder<T>>) override {
        if (_num_subspaces == 0 && _type.count_mapped_dimensions() == 0) {
            // add required dense subspace
            add_subspace({});
        }
        // note: _num_subspaces * _dsss == _cells.size()
        return std::make_unique<StreamedValue<T>>(std::move(_type),
                                                  std::move(_cells),
                                                  _num_subspaces,
                                                  _labels.extract_buffer());
    }

};

} // namespace
