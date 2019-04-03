// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "common.h"
#include <memory>
#include <vector>

namespace vespalib { class nbostream; }

namespace vespalib::tensor {

class Tensor;

/**
 * Class for serializing a tensor.
 */
class TypedBinaryFormat
{
public:
    static void serialize(nbostream &stream, const Tensor &tensor, SerializeFormat format);
    static void serialize(nbostream &stream, const Tensor &tensor) {
        serialize(stream, tensor, SerializeFormat::DOUBLE);
    }

    static std::unique_ptr<Tensor> deserialize(nbostream &stream);
    
    // This is a temporary method until we get full support for typed tensors
    template <typename T>
    static void deserializeCellsOnlyFromDenseTensors(nbostream &stream, std::vector<T> & cells);
};

}
