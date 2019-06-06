// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace vespalib { class nbostream; }

namespace vespalib::tensor {

class Tensor;

/**
 * Class for serializing a tensor.
 */
class SparseBinaryFormat
{
public:
    static void serialize(nbostream &stream, const Tensor &tensor);
    static std::unique_ptr<Tensor> deserialize(nbostream &stream);
};

}
