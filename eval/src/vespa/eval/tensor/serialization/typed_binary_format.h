// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <cstdint>

namespace vespalib {

class nbostream;

namespace tensor {

struct Tensor;
class TensorBuilder;

/**
 * Class for serializing a tensor.
 */
class TypedBinaryFormat
{
    static constexpr uint32_t SPARSE_BINARY_FORMAT_TYPE = 1u;
    static constexpr uint32_t DENSE_BINARY_FORMAT_TYPE = 2u;
    static constexpr uint32_t MIXED_BINARY_FORMAT_TYPE = 3u;
public:
    static void serialize(nbostream &stream, const Tensor &tensor);
    static std::unique_ptr<Tensor> deserialize(nbostream &stream);
};

} // namespace vespalib::tensor
} // namespace vespalib
