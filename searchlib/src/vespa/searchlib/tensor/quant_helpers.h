// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/typed_cells.h>
#include <vespa/vespalib/quant/quantized_vector.h>

#include <memory>

using vespalib::eval::CellType;
using vespalib::eval::Int8Float;
using vespalib::eval::TypedCells;
using vespalib::quant::QuantizedVector;

namespace search::tensor {

inline QuantizedVector as_quantized(TypedCells cells) {
    assert(cells.type == CellType::INT8);
    auto      bytes = static_cast<const uint8_t*>(cells.data);
    std::span span(bytes, cells.size);
    return QuantizedVector(span);
}

inline QuantizedVector as_quantized(std::span<const Int8Float> cells) {
    auto      bytes = reinterpret_cast<const uint8_t*>(cells.data());
    std::span span(bytes, cells.size());
    return QuantizedVector(span);
}

} // namespace search::tensor
