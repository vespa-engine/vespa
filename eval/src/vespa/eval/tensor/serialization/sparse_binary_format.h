// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib {

class nbostream;

namespace tensor {

class Tensor;
class TensorBuilder;

/**
 * Class for serializing a tensor.
 */
class SparseBinaryFormat
{
public:
    static void serialize(nbostream &stream, const Tensor &tensor);
    static void deserialize(nbostream &stream, TensorBuilder &builder);
};

} // namespace vespalib::tensor
} // namespace vespalib
