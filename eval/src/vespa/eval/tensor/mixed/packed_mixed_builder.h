// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "packed_mixed_tensor.h"

namespace vespalib::eval {

template <typename T>
class PackedMixedBuilder : public ValueBuilder<T>
{
private:
    const ValueType & _type;
    size_t _subspace_size;
    std::vector<T> _cells;
    PackedMappingsBuilder _mappings_builder;
public:
    PackedMixedBuilder(const ValueType &type,
                             size_t num_mapped_in,
                             size_t subspace_size_in,
                             size_t expect_subspaces)
      : _type(type),
        _subspace_size(subspace_size_in),
        _cells(),
        _mappings_builder(num_mapped_in)
    {
        _cells.reserve(_subspace_size * expect_subspaces);
    }

    ~PackedMixedBuilder() override = default;
        
    ArrayRef<T> add_subspace(const std::vector<vespalib::stringref> &addr) override;
    std::unique_ptr<NewValue> build(std::unique_ptr<ValueBuilder<T>> self) override;
};

} // namespace
