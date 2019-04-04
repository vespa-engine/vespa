// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "common.h"
#include <memory>
#include <vector>

namespace vespalib { class nbostream; }

namespace vespalib::tensor {

class DenseTensor;
class DenseTensorView;

/**
 * Class for serializing a dense tensor.
 */
class DenseBinaryFormat
{
public:
    DenseBinaryFormat(SerializeFormat format) : _format(format) { }
    void serialize(nbostream &stream, const DenseTensorView &tensor);
    std::unique_ptr<DenseTensor> deserialize(nbostream &stream);
    
    // This is a temporary method untill we get full support for typed tensors
    template <typename T>
    void deserializeCellsOnly(nbostream &stream, std::vector<T> & cells);
private:
    SerializeFormat _format;
};

}
