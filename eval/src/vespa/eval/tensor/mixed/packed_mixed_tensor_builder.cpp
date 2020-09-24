// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "packed_mixed_tensor_builder.h"

namespace vespalib::eval::packed_mixed_tensor {

template <typename T>
ArrayRef<T> 
PackedMixedTensorBuilder<T>::add_subspace(const std::vector<vespalib::stringref> &addr)
{
    uint32_t idx = _mappings_builder.add_mapping_for(addr);
    size_t offset = idx * _subspace_size;
    assert(offset <= _cells.size());
    if (offset == _cells.size()) {
        _cells.resize(offset + _subspace_size);
    }
    return ArrayRef<T>(&_cells[offset], _subspace_size);
}


template <typename T>
std::unique_ptr<Value>
PackedMixedTensorBuilder<T>::build(std::unique_ptr<ValueBuilder<T>>)
{
    size_t self_size = sizeof(PackedMixedTensor);
    size_t mappings_size = _mappings_builder.extra_memory();
    // align:
    mappings_size += 15ul;
    mappings_size &= ~15ul;
    size_t cells_size = sizeof(T) * _cells.size();
    size_t total_size = self_size + mappings_size + cells_size;

    char *mem = (char *) operator new(total_size);
    char *mappings_mem = mem + self_size;
    char *cells_mem = mappings_mem + mappings_size;

    // fill mapping data:
    auto mappings = _mappings_builder.target_memory(mappings_mem, cells_mem);

    // copy cells:
    memcpy(cells_mem, &_cells[0], cells_size);
    ConstArrayRef<T> cells((T *)cells_mem, _cells.size());

    PackedMixedTensor * built =
        new (mem) PackedMixedTensor(_type, TypedCells(cells), mappings);

    return std::unique_ptr<PackedMixedTensor>(built);
}

template class PackedMixedTensorBuilder<float>;
template class PackedMixedTensorBuilder<double>;

} // namespace
