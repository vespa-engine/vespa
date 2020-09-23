// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "value_codec.h"
#include "codec.h"
#include <vespa/vespalib/util/typify.h>

using namespace vespalib::eval::codec;

namespace vespalib::eval {

void encode_mapped_labels(nbostream &output, size_t num_mapped_dims, const std::vector<vespalib::stringref> &addr) {
    for (size_t i = 0; i < num_mapped_dims; ++i) {
        output.writeSmallString(addr[i]);
    }
}

void decode_mapped_labels(nbostream &input, size_t num_mapped_dims, std::vector<vespalib::string> &addr) {
    for (size_t i = 0; i < num_mapped_dims; ++i) {
        vespalib::string name;
        input.readSmallString(name);
        addr[i] = name;
    }
}

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
    std::vector<vespalib::string> address;
    std::vector<vespalib::stringref> addr_refs;
    DecodeState(const ValueType &type_in,
                size_t block_size_in,
                size_t num_blocks_in,
                size_t num_mapped_dims_in)
      : type(type_in),
        block_size(block_size_in),
        num_blocks(num_blocks_in),
        num_mapped_dims(num_mapped_dims_in),
        address(num_mapped_dims_in),
        addr_refs(num_mapped_dims_in)
    {}
    void fix_refs() {
        for (size_t j = 0; j < num_mapped_dims; ++j) {
            addr_refs[j] = address[j];
        }
    }
};

struct ContentDecoder {
    template<typename T>
    static std::unique_ptr<Value> invoke(nbostream &input, DecodeState &state, const ValueBuilderFactory &factory) {
        auto builder = factory.create_value_builder<T>(state.type, state.num_mapped_dims, state.block_size, state.num_blocks);
        for (size_t i = 0; i < state.num_blocks; ++i) {
            decode_mapped_labels(input, state.num_mapped_dims, state.address);
            state.fix_refs();
            auto block_cells = builder->add_subspace(state.addr_refs);
            decode_cells(input, state.block_size, block_cells);
        }
        return builder->build(std::move(builder));
    }
};

std::unique_ptr<Value> new_decode(nbostream &input, const ValueBuilderFactory &factory) {
    Format format(input.getInt1_4Bytes());
    ValueType type = decode_type(input, format);
    TypeMeta meta(type);
    const size_t num_blocks = maybe_decode_num_blocks(input, meta, format);
    DecodeState state(type, meta.block_size, num_blocks, meta.mapped.size());
    return typify_invoke<1,TypifyCellType,ContentDecoder>(meta.cell_type, input, state, factory);
}

} // namespace
