// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "empty_subspace.h"
#include "subspace_type.h"
#include "vector_bundle.h"
#include <vespa/vespalib/datastore/aligner.h>
#include <vespa/vespalib/util/arrayref.h>
#include <vespa/vespalib/util/string_id.h>
#include <cstddef>
#include <memory>

namespace vespalib { class nbostream; }

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
 *  num_subspaces_and_flag
 *   31 least significant bits is num_subspaces      - number of subspaces
 *    1 bit to signal that reclaim_labels should be a noop (i.e. buffer has been copied
 *      as part of compaction or fallback copy (due to datastore buffer fallback resize)).
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
    SubspaceType                      _subspace_type;
    uint32_t                          _num_mapped_dimensions;
    uint32_t                          _min_alignment;
    std::vector<vespalib::string_id>  _addr;
    std::vector<vespalib::string_id*> _addr_refs;
    EmptySubspace                     _empty;

    using Aligner = vespalib::datastore::Aligner<vespalib::datastore::dynamic_alignment>;

    static constexpr size_t CELLS_ALIGNMENT = 16;
    static constexpr size_t CELLS_ALIGNMENT_MEM_SIZE_MIN = 32;
    static constexpr uint32_t num_subspaces_mask = ((1u << 31) - 1);
    static constexpr uint32_t skip_reclaim_labels_mask = (1u << 31);

    static constexpr size_t get_num_subspaces_size() noexcept { return sizeof(uint32_t); }
    static constexpr size_t get_labels_offset() noexcept { return get_num_subspaces_size(); }
    size_t get_cells_mem_size(uint32_t num_subspaces) const noexcept {
        return _subspace_type.mem_size() * num_subspaces;
    }
    auto select_aligner(size_t cells_mem_size) const noexcept {
        return Aligner((cells_mem_size < CELLS_ALIGNMENT_MEM_SIZE_MIN) ? _min_alignment : CELLS_ALIGNMENT);
    }
    size_t get_labels_mem_size(uint32_t num_subspaces) const noexcept {
        return sizeof(vespalib::string_id) * _num_mapped_dimensions * num_subspaces;
    }
    size_t get_cells_offset(uint32_t num_subspaces, auto aligner) const noexcept {
        return aligner.align(get_labels_offset() + get_labels_mem_size(num_subspaces));
    }
    uint32_t get_num_subspaces_and_flag(vespalib::ConstArrayRef<char> buf) const noexcept;
    void set_skip_reclaim_labels(vespalib::ArrayRef<char> buf, uint32_t num_subspaces_and_flag) const noexcept;
    static uint32_t get_num_subspaces(uint32_t num_subspaces_and_flag) noexcept {
        return num_subspaces_and_flag & num_subspaces_mask;
    }
    static bool get_skip_reclaim_labels(uint32_t num_subspaces_and_flag) noexcept {
        return (num_subspaces_and_flag & skip_reclaim_labels_mask) != 0;
    }
    uint32_t get_num_subspaces(vespalib::ConstArrayRef<char> buf) const noexcept {
        return get_num_subspaces(get_num_subspaces_and_flag(buf));
    }
public:
    size_t get_array_size(uint32_t num_subspaces) const noexcept {
        auto cells_mem_size = get_cells_mem_size(num_subspaces);
        auto aligner = select_aligner(cells_mem_size);
        return get_cells_offset(num_subspaces, aligner) + aligner.align(cells_mem_size);
    }
    TensorBufferOperations(const vespalib::eval::ValueType& tensor_type);
    ~TensorBufferOperations();
    TensorBufferOperations(const TensorBufferOperations&) = delete;
    TensorBufferOperations(TensorBufferOperations&&) = delete;
    TensorBufferOperations& operator=(const TensorBufferOperations&) = delete;
    TensorBufferOperations& operator=(TensorBufferOperations&&) = delete;
    void store_tensor(vespalib::ArrayRef<char> buf, const vespalib::eval::Value& tensor);
    std::unique_ptr<vespalib::eval::Value> make_fast_view(vespalib::ConstArrayRef<char> buf, const vespalib::eval::ValueType& tensor_type) const;

    // Mark that reclaim_labels should be skipped for old buffer after copying tensor buffer
    void copied_labels(vespalib::ArrayRef<char> buf) const;
    // Decrease reference counts for labels and set skip flag unless skip flag is set.
    void reclaim_labels(vespalib::ArrayRef<char> buf) const;
    // Serialize stored tensor to target (used when saving attribute)
    void encode_stored_tensor(vespalib::ConstArrayRef<char> buf, const vespalib::eval::ValueType& type, vespalib::nbostream& target) const;
    vespalib::eval::TypedCells get_empty_subspace() const noexcept {
        return _empty.cells();
    }
    VectorBundle get_vectors(vespalib::ConstArrayRef<char> buf) const {
        auto num_subspaces = get_num_subspaces(buf);
        auto cells_mem_size = get_cells_mem_size(num_subspaces);
        auto aligner = select_aligner(cells_mem_size);
        return VectorBundle(buf.data() + get_cells_offset(num_subspaces, aligner), num_subspaces, _subspace_type);
    }
};

}
