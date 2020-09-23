// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "value_codec.h"
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/typify.h>

namespace vespalib::eval {

namespace {

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

void maybe_encode_cell_type(nbostream &output, const Format &format, const TypeMeta &meta) {
    if (format.with_cell_type) {
        output.putInt1_4Bytes(cell_type_to_id(meta.cell_type));
    }
}

void encode_type(nbostream &output, const Format &format, const ValueType &type, const TypeMeta &meta) {
    maybe_encode_cell_type(output, format, meta);
    if (format.is_sparse) {
        output.putInt1_4Bytes(meta.mapped.size());
        for (size_t idx: meta.mapped) {
            output.writeSmallString(type.dimensions()[idx].name);
        }
    }
    if (format.is_dense) {
        output.putInt1_4Bytes(meta.indexed.size());
        for (size_t idx: meta.indexed) {
            output.writeSmallString(type.dimensions()[idx].name);
            output.putInt1_4Bytes(type.dimensions()[idx].size);
        }
    }
}

void maybe_encode_num_blocks(nbostream &output, const TypeMeta &meta, size_t num_blocks) {
    if ((meta.mapped.size() > 0)) {
        output.putInt1_4Bytes(num_blocks);
    }
}

CellType maybe_decode_cell_type(nbostream &input, const Format &format) {
    if (format.with_cell_type) {
        return id_to_cell_type(input.getInt1_4Bytes());
    }
    return CellType::DOUBLE;
}

ValueType decode_type(nbostream &input, const Format &format) {
    CellType cell_type = maybe_decode_cell_type(input, format);
    std::vector<ValueType::Dimension> dim_list;
    if (format.is_sparse) {
        size_t cnt = input.getInt1_4Bytes();
        for (size_t i = 0; i < cnt; ++i) {
            vespalib::string name;
            input.readSmallString(name);
            dim_list.emplace_back(name);
        }
    }
    if (format.is_dense) {
        size_t cnt = input.getInt1_4Bytes();
        for (size_t i = 0; i < cnt; ++i) {
            vespalib::string name;
            input.readSmallString(name);
            dim_list.emplace_back(name, input.getInt1_4Bytes());
        }
    }
    return ValueType::tensor_type(std::move(dim_list), cell_type);
}

size_t maybe_decode_num_blocks(nbostream &input, const TypeMeta &meta, const Format &format) {
    if ((meta.mapped.size() > 0) || !format.is_dense) {
        return input.getInt1_4Bytes();
    }
    return 1;
}

void encode_mapped_labels(nbostream &output, size_t num_mapped_dims, const std::vector<vespalib::stringref> &addr) {
    for (size_t i = 0; i < num_mapped_dims; ++i) {
        output.writeSmallString(addr[i]);
    }
}

void decode_mapped_labels(nbostream &input, size_t num_mapped_dims, std::vector<vespalib::stringref> &addr) {
    for (size_t i = 0; i < num_mapped_dims; ++i) {
        size_t strSize = input.getInt1_4Bytes();
        addr[i] = vespalib::stringref(input.peek(), strSize);
        input.adjustReadPos(strSize);
    }
}


template<typename T>
void decode_cells(nbostream &input, size_t num_cells, ArrayRef<T> dst)
{
    T value;
    for (size_t i = 0; i < num_cells; ++i) {
        input >> value;
        dst[i] = value;
    }
}

struct DecodeState {
    const ValueType &type;
    const size_t block_size;
    const size_t num_blocks;
    const size_t num_mapped_dims;
};

struct ContentDecoder {
    template<typename T>
    static std::unique_ptr<Value> invoke(nbostream &input, const DecodeState &state, const ValueBuilderFactory &factory) {
        std::vector<vespalib::stringref> address(state.num_mapped_dims);
        auto builder = factory.create_value_builder<T>(state.type, state.num_mapped_dims, state.block_size, state.num_blocks);
        for (size_t i = 0; i < state.num_blocks; ++i) {
            decode_mapped_labels(input, state.num_mapped_dims, address);
            auto block_cells = builder->add_subspace(address);
            decode_cells(input, state.block_size, block_cells);
        }
        return builder->build(std::move(builder));
    }
};

} // namespace <unnamed>

void new_encode(const Value &value, nbostream &output) {
    TypeMeta meta(value.type());
    Format format(meta);
    output.putInt1_4Bytes(format.tag);
    encode_type(output, format, value.type(), meta);
    maybe_encode_num_blocks(output, meta, value.cells().size / meta.block_size);
    std::vector<vespalib::stringref> address(meta.mapped.size());
    std::vector<vespalib::stringref*> a_refs(meta.mapped.size());
    for (size_t i = 0; i < meta.mapped.size(); ++i) {
        a_refs[i] = &address[i];
    }
    auto view = value.index().create_view({});
    view->lookup({});
    size_t subspace;
    while (view->next_result(a_refs, subspace)) {
        encode_mapped_labels(output, meta.mapped.size(), address);
        if (meta.cell_type == CellType::FLOAT) {
            auto iter = value.cells().typify<float>().begin();
            iter += (subspace * meta.block_size);
            for (size_t i = 0; i < meta.block_size; ++i) {
                output << (float) *iter++;
            }
        } else {
            auto iter = value.cells().typify<double>().begin();
            iter += (subspace * meta.block_size);
            for (size_t i = 0; i < meta.block_size; ++i) {
                output << *iter++;
            }
        }
    }
}

std::unique_ptr<Value> new_decode(nbostream &input, const ValueBuilderFactory &factory) {
    Format format(input.getInt1_4Bytes());
    ValueType type = decode_type(input, format);
    TypeMeta meta(type);
    const size_t num_blocks = maybe_decode_num_blocks(input, meta, format);
    DecodeState state{type, meta.block_size, num_blocks, meta.mapped.size()};
    return typify_invoke<1,TypifyCellType,ContentDecoder>(meta.cell_type, input, state, factory);
}

} // namespace
