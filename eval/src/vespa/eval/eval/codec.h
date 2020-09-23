// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "simple_tensor.h"
#include <vespa/vespalib/objects/nbostream.h>
#include <cassert>

namespace vespalib::eval::codec {

using CellType = ValueType::CellType;
using IndexList = std::vector<size_t>;

constexpr uint32_t DOUBLE_CELL_TYPE = 0;
constexpr uint32_t FLOAT_CELL_TYPE = 1;

inline uint32_t cell_type_to_id(CellType cell_type) {
    switch (cell_type) {
    case CellType::DOUBLE: return DOUBLE_CELL_TYPE;
    case CellType::FLOAT: return FLOAT_CELL_TYPE;
    }
    abort();
}

inline CellType id_to_cell_type(uint32_t id) {
    switch (id) {
    case DOUBLE_CELL_TYPE: return CellType::DOUBLE;
    case FLOAT_CELL_TYPE: return CellType::FLOAT;
    }
    abort();
}

/**
 * Meta information about how a type can be decomposed into mapped and
 * indexed dimensions and also how large each block is. A block is a
 * dense-subspace consisting of all indexed dimensions that is
 * uniquely specified by the labels of all mapped dimensions.
 **/
struct TypeMeta {
    IndexList mapped;
    IndexList indexed;
    size_t block_size;
    CellType cell_type;
    explicit TypeMeta(const ValueType &type)
        : mapped(),
          indexed(),
          block_size(1),
          cell_type(type.cell_type())
    {
        for (size_t i = 0; i < type.dimensions().size(); ++i) {
            const auto &dimension = type.dimensions()[i];
            if (dimension.is_mapped()) {
                mapped.push_back(i);
            } else {
                block_size *= dimension.size;
                indexed.push_back(i);
            }
        }
    }
    ~TypeMeta() {}
};

struct Format {
    bool     is_sparse;
    bool     is_dense;
    bool     with_cell_type;
    uint32_t tag;
    explicit Format(const TypeMeta &meta)
        : is_sparse(meta.mapped.size() > 0),
          is_dense((meta.indexed.size() > 0) || !is_sparse),
          with_cell_type(meta.cell_type != CellType::DOUBLE),
          tag((is_sparse ? 0x1 : 0) | (is_dense ? 0x2 : 0) | (with_cell_type ? 0x4 : 0)) {}
    explicit Format(uint32_t tag_in)
        : is_sparse((tag_in & 0x1) != 0),
          is_dense((tag_in & 0x2) != 0),
          with_cell_type((tag_in & 0x4) != 0),
          tag(tag_in) {}
    ~Format() {}
};

inline void maybe_encode_cell_type(nbostream &output, const Format &format, const TypeMeta &meta) {
    if (format.with_cell_type) {
        output.putInt1_4Bytes(cell_type_to_id(meta.cell_type));
    }
}

void encode_type(nbostream &output, const Format &format, const ValueType &type, const TypeMeta &meta);

inline void maybe_encode_num_blocks(nbostream &output, const TypeMeta &meta, size_t num_blocks) {
    if ((meta.mapped.size() > 0)) {
        output.putInt1_4Bytes(num_blocks);
    }
}

inline CellType maybe_decode_cell_type(nbostream &input, const Format &format) {
    if (format.with_cell_type) {
        return id_to_cell_type(input.getInt1_4Bytes());
    }
    return CellType::DOUBLE;
}

ValueType decode_type(nbostream &input, const Format &format);

inline size_t maybe_decode_num_blocks(nbostream &input, const TypeMeta &meta, const Format &format) {
    if ((meta.mapped.size() > 0) || !format.is_dense) {
        return input.getInt1_4Bytes();
    }
    return 1;
}

} // namespace vespalib::eval::codec
