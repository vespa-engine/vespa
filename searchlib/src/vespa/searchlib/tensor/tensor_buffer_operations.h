// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/cell_type.h>
#include <vespa/vespalib/util/string_id.h>
#include <cstddef>
#include <memory>

namespace vespalib {
template <typename T> class ArrayRef;
template <typename T> class ConstArrayRef;
class nbostream;
}

namespace vespalib::eval {
struct Value;
class ValueType;
}

namespace search::tensor {

/*
 * Class used to store a tensor in a buffer and make tensor views based on
 * buffer content.
 *
 * Layout of buffer is:
 *
 * num_subspaces                                     - number of subspaces
 * labels[num_subspaces * _num_mapped_dimensions]    - array of labels for sparse dimensions
 * padding                                           - to align start of cells
 * cells[num_subspaces * _dense_subspaces_size]      - array of tensor cell values
 * padding                                           - to align start of next buffer
 *
 * Alignment is dynamic, based on cell type, memory used by tensor cell values and
 * alignment required for reading num_subspaces and labels array.
 */
class TensorBufferOperations
{
    uint32_t                          _num_mapped_dimensions;
    uint32_t                          _cell_mem_size;
    uint32_t                          _min_alignment;
    size_t                            _dense_subspace_size;
    vespalib::eval::CellType          _cell_type;
    std::vector<vespalib::string_id>  _addr;
    std::vector<vespalib::string_id*> _addr_refs;

    static constexpr size_t CELLS_ALIGNMENT = 16;
    static constexpr size_t CELLS_ALIGNMENT_MEM_SIZE_MIN = 32;

    static constexpr size_t get_num_subspaces_size() noexcept { return sizeof(uint32_t); }
    static constexpr size_t get_labels_offset() noexcept { return get_num_subspaces_size(); }
    static size_t calc_aligned(size_t unaligned, size_t alignment) noexcept {
        return (unaligned + alignment - 1) & (- alignment);
    }
    size_t get_cells_mem_size(uint32_t num_subspaces) const noexcept {
        return _dense_subspace_size * _cell_mem_size * num_subspaces;
    }
    size_t select_alignment(size_t cells_mem_size) const noexcept {
        return (cells_mem_size < CELLS_ALIGNMENT_MEM_SIZE_MIN) ? _min_alignment : CELLS_ALIGNMENT;
    }
    size_t get_labels_mem_size(uint32_t num_subspaces) const noexcept {
        return sizeof(vespalib::string_id) * _num_mapped_dimensions * num_subspaces;
    }
    size_t get_cells_offset(uint32_t num_subspaces, size_t alignment) const noexcept {
        return calc_aligned(get_labels_offset() + get_labels_mem_size(num_subspaces), alignment);
    }
    uint32_t get_num_subspaces(vespalib::ConstArrayRef<char> buf) const noexcept;
public:
    size_t get_array_size(uint32_t num_subspaces) const noexcept {
        auto cells_mem_size = get_cells_mem_size(num_subspaces);
        auto alignment = select_alignment(cells_mem_size);
        return get_cells_offset(num_subspaces, alignment) + calc_aligned(cells_mem_size, alignment);
    }
    TensorBufferOperations(const vespalib::eval::ValueType& tensor_type);
    ~TensorBufferOperations();
    TensorBufferOperations(const TensorBufferOperations&) = delete;
    TensorBufferOperations(TensorBufferOperations&&) = delete;
    TensorBufferOperations& operator=(const TensorBufferOperations&) = delete;
    TensorBufferOperations& operator=(TensorBufferOperations&&) = delete;
    void store_tensor(vespalib::ArrayRef<char> buf, const vespalib::eval::Value& tensor);
    std::unique_ptr<vespalib::eval::Value> make_fast_view(vespalib::ConstArrayRef<char> buf, const vespalib::eval::ValueType& tensor_type) const;

    // Increase reference counts for labels after copying tensor buffer
    void copied_labels(vespalib::ConstArrayRef<char> buf) const;
    // Decrease reference counts for labels and invalidate them
    void reclaim_labels(vespalib::ArrayRef<char> buf) const;
    // Serialize stored tensor to target (used when saving attribute)
    void encode_stored_tensor(vespalib::ConstArrayRef<char> buf, const vespalib::eval::ValueType& type, vespalib::nbostream& target) const;
};

}
