// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "value_codec.h"
#include "tensor_spec.h"
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

struct Format {
    bool     has_sparse;
    bool     has_dense;
    bool     with_cell_type;
    uint32_t tag;
    explicit Format(const ValueType &type)
        : has_sparse(type.count_mapped_dimensions() > 0),
          has_dense((type.count_indexed_dimensions() > 0) || !has_sparse),
          with_cell_type(type.cell_type() != CellType::DOUBLE),
          tag((has_sparse ? 0x1 : 0) | (has_dense ? 0x2 : 0) | (with_cell_type ? 0x4 : 0)) {}
    explicit Format(uint32_t tag_in)
        : has_sparse((tag_in & 0x1) != 0),
          has_dense((tag_in & 0x2) != 0),
          with_cell_type((tag_in & 0x4) != 0),
          tag(tag_in) {}
    ~Format() {}
};

void maybe_encode_cell_type(nbostream &output, const Format &format, CellType cell_type) {
    if (format.with_cell_type) {
        output.putInt1_4Bytes(cell_type_to_id(cell_type));
    }
}

void encode_type(nbostream &output, const Format &format, const ValueType &type) {
    maybe_encode_cell_type(output, format, type.cell_type());
    if (format.has_sparse) {
        const auto & dims = type.mapped_dimensions();
        output.putInt1_4Bytes(dims.size());
        for (const auto & dim : dims) {
            output.writeSmallString(dim.name);
        }
    }
    if (format.has_dense) {
        const auto & dims = type.indexed_dimensions();
        output.putInt1_4Bytes(dims.size());
        for (const auto & dim : dims) {
            output.writeSmallString(dim.name);
            output.putInt1_4Bytes(dim.size);
        }
    }
}

void maybe_encode_num_blocks(nbostream &output, bool has_mapped_dims, size_t num_blocks) {
    if (has_mapped_dims) {
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
    if (format.has_sparse) {
        size_t cnt = input.getInt1_4Bytes();
        for (size_t i = 0; i < cnt; ++i) {
            vespalib::string name;
            input.readSmallString(name);
            dim_list.emplace_back(name);
        }
    }
    if (format.has_dense) {
        size_t cnt = input.getInt1_4Bytes();
        for (size_t i = 0; i < cnt; ++i) {
            vespalib::string name;
            input.readSmallString(name);
            dim_list.emplace_back(name, input.getInt1_4Bytes());
        }
    }
    return ValueType::tensor_type(std::move(dim_list), cell_type);
}

size_t maybe_decode_num_blocks(nbostream &input, bool has_mapped_dims, const Format &format) {
    if (has_mapped_dims || !format.has_dense) {
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
    const size_t subspace_size;
    const size_t num_blocks;
    const size_t num_mapped_dims;
};

struct ContentDecoder {
    template<typename T>
    static std::unique_ptr<Value> invoke(nbostream &input, const DecodeState &state, const ValueBuilderFactory &factory) {
        std::vector<vespalib::stringref> address(state.num_mapped_dims);
        auto builder = factory.create_value_builder<T>(state.type, state.num_mapped_dims, state.subspace_size, state.num_blocks);
        for (size_t i = 0; i < state.num_blocks; ++i) {
            decode_mapped_labels(input, state.num_mapped_dims, address);
            auto block_cells = builder->add_subspace(address);
            decode_cells(input, state.subspace_size, block_cells);
        }
        return builder->build(std::move(builder));
    }
};

struct CreateValueFromTensorSpec {
    template <typename T> static std::unique_ptr<Value> invoke(const ValueType &type, const TensorSpec &spec, const ValueBuilderFactory &factory) {
        using SparseKey = std::vector<vespalib::stringref>;
        using DenseMap = std::map<size_t,T>;
        std::map<SparseKey,DenseMap> map;
        for (const auto &entry: spec.cells()) {
            SparseKey sparse_key;
            size_t dense_key = 0;
            for (const auto &dim: type.dimensions()) {
                auto pos = entry.first.find(dim.name);
                assert(pos != entry.first.end());
                assert(pos->second.is_mapped() == dim.is_mapped());
                if (dim.is_mapped()) {
                    sparse_key.emplace_back(pos->second.name);
                } else {
                    dense_key = (dense_key * dim.size) + pos->second.index;
                }
            }
            map[sparse_key][dense_key] = entry.second;
        }
        // if spec is missing the required dense space, add it here:
        if (map.empty() && type.count_mapped_dimensions() == 0) {
            map[{}][0] = 0;
        }
        auto builder = factory.create_value_builder<T>(type, type.count_mapped_dimensions(), type.dense_subspace_size(), map.size());
        for (const auto &entry: map) {
            auto subspace = builder->add_subspace(entry.first);
            for (const auto &cell: entry.second) {
                subspace[cell.first] = cell.second;
            }
        }
        return builder->build(std::move(builder));
    }
};

struct CreateTensorSpecFromValue {
    template <typename T> static TensorSpec invoke(const Value &value) {
        auto cells = value.cells().typify<T>();
        TensorSpec spec(value.type().to_spec());
        size_t subspace_id = 0;
        size_t subspace_size = value.type().dense_subspace_size();
        std::vector<vespalib::stringref> labels(value.type().count_mapped_dimensions());
        std::vector<vespalib::stringref*> label_refs;
        for (auto &label: labels) {
            label_refs.push_back(&label);
        }
        auto view = value.index().create_view({});
        view->lookup({});
        while (view->next_result(label_refs, subspace_id)) {
            size_t label_idx = 0;
            TensorSpec::Address addr;
            for (const auto &dim: value.type().dimensions()) {
                if (dim.is_mapped()) {
                    addr.emplace(dim.name, labels[label_idx++]);
                }
            }
            for (size_t i = 0; i < subspace_size; ++i) {
                size_t dense_key = i;
                for (auto dim = value.type().dimensions().rbegin();
                     dim != value.type().dimensions().rend(); ++dim)
                {
                    if (dim->is_indexed()) {
                        size_t label = dense_key % dim->size;
                        addr.emplace(dim->name, label).first->second = TensorSpec::Label(label);
                        dense_key /= dim->size;
                    }
                }
                spec.add(addr, cells[(subspace_size * subspace_id) + i]);
            }
        }
        return spec;
    }
};

} // namespace <unnamed>

struct EncodeState {
    size_t num_mapped_dims;
    size_t subspace_size;
};

struct ContentEncoder {
    template<typename T>
    static void invoke(const Value &value, const EncodeState &state, nbostream &output) {
        std::vector<vespalib::stringref> address(state.num_mapped_dims);
        std::vector<vespalib::stringref*> a_refs(state.num_mapped_dims);;
        for (size_t i = 0; i < state.num_mapped_dims; ++i) {
            a_refs[i] = &address[i];
        }
        auto view = value.index().create_view({});
        view->lookup({});
        size_t subspace;
        while (view->next_result(a_refs, subspace)) {
            encode_mapped_labels(output, state.num_mapped_dims, address);
            auto iter = value.cells().typify<T>().begin();
            iter += (subspace * state.subspace_size);
            for (size_t i = 0; i < state.subspace_size; ++i) {
                output << *iter++;
            }
        }
    }
};
    
void encode_value(const Value &value, nbostream &output) {
    size_t num_mapped_dims = value.type().count_mapped_dimensions();
    size_t dense_subspace_size = value.type().dense_subspace_size();
    Format format(value.type());
    output.putInt1_4Bytes(format.tag);
    encode_type(output, format, value.type());
    maybe_encode_num_blocks(output, (num_mapped_dims > 0), value.cells().size / dense_subspace_size);
    EncodeState state{num_mapped_dims, dense_subspace_size};
    typify_invoke<1,TypifyCellType,ContentEncoder>(value.type().cell_type(), value, state, output);
}

std::unique_ptr<Value> decode_value(nbostream &input, const ValueBuilderFactory &factory) {
    Format format(input.getInt1_4Bytes());
    ValueType type = decode_type(input, format);
    size_t num_mapped_dims = type.count_mapped_dimensions();
    size_t dense_subspace_size = type.dense_subspace_size();
    const size_t num_blocks = maybe_decode_num_blocks(input, (num_mapped_dims > 0), format);
    DecodeState state{type, dense_subspace_size, num_blocks, num_mapped_dims};
    return typify_invoke<1,TypifyCellType,ContentDecoder>(type.cell_type(), input, state, factory);
}

//-----------------------------------------------------------------------------

std::unique_ptr<Value> value_from_spec(const TensorSpec &spec, const ValueBuilderFactory &factory) {
    ValueType type = ValueType::from_spec(spec.type());
    assert(!type.is_error());
    return typify_invoke<1,TypifyCellType,CreateValueFromTensorSpec>(type.cell_type(), type, spec, factory);
}

TensorSpec spec_from_value(const Value &value) {
    return typify_invoke<1,TypifyCellType,CreateTensorSpecFromValue>(value.type().cell_type(), value);
}

//-----------------------------------------------------------------------------


} // namespace
