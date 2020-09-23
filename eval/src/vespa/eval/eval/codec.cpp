// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "codec.h"

namespace vespalib::eval::codec {

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

} // namespace vespalib::eval::codec
