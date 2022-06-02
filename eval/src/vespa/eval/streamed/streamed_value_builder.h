// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "streamed_value.h"
#include <vespa/eval/eval/value_builder_factory.h>
#include <vespa/vespalib/util/shared_string_repo.h>

namespace vespalib::eval {

 /**
  *  Builder for StreamedValue objects.
  **/
template <typename T>
class StreamedValueBuilder : public ValueBuilder<T>
{
private:
    using Handles = SharedStringRepo::Handles;

    ValueType _type;
    size_t _num_mapped_dimensions;
    size_t _dense_subspace_size;
    std::vector<T> _cells;
    size_t _num_subspaces;
    Handles _labels;
public:
    StreamedValueBuilder(const ValueType &type,
                         size_t num_mapped_in,
                         size_t subspace_size_in,
                         size_t expected_subspaces)
      : _type(type),
        _num_mapped_dimensions(num_mapped_in),
        _dense_subspace_size(subspace_size_in),
        _cells(),
        _num_subspaces(0),
        _labels()
    {
        _cells.reserve(subspace_size_in * expected_subspaces);
        _labels.reserve(num_mapped_in * expected_subspaces);
    };

    ~StreamedValueBuilder();

    ArrayRef<T> add_subspace(ConstArrayRef<vespalib::stringref> addr) override {
        for (auto label : addr) {
            _labels.add(label);
        }
        size_t old_sz = _cells.size();
        _cells.resize(old_sz + _dense_subspace_size);
        _num_subspaces++;
        return ArrayRef<T>(&_cells[old_sz], _dense_subspace_size);
    }

    ArrayRef<T> add_subspace(ConstArrayRef<string_id> addr) override {
        for (auto label : addr) {
            _labels.push_back(label);
        }
        size_t old_sz = _cells.size();
        _cells.resize(old_sz + _dense_subspace_size);
        _num_subspaces++;
        return ArrayRef<T>(&_cells[old_sz], _dense_subspace_size);
    }

    std::unique_ptr<Value> build(std::unique_ptr<ValueBuilder<T>>) override {
        if (_num_mapped_dimensions == 0) {
            assert(_num_subspaces == 1);
        }
        assert(_num_subspaces * _dense_subspace_size == _cells.size());
        return std::make_unique<StreamedValue<T>>(std::move(_type),
                                                  _num_mapped_dimensions,
                                                  std::move(_cells),
                                                  _num_subspaces,
                                                  std::move(_labels));
    }

};

} // namespace
