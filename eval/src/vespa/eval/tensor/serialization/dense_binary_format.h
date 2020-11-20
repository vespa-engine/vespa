// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vector>
#include <vespa/eval/eval/value_type.h>

namespace vespalib { class nbostream; }

namespace vespalib::tensor {

class DenseTensorView;

/**
 * Class for serializing a dense tensor.
 */
class DenseBinaryFormat
{
public:
    using CellType = vespalib::eval::CellType;

    static void serialize(nbostream &stream, const DenseTensorView &tensor);
    static std::unique_ptr<DenseTensorView> deserialize(nbostream &stream, CellType cell_type);

    // This is a temporary method untill we get full support for typed tensors
    template <typename T>
    static void deserializeCellsOnly(nbostream &stream, std::vector<T> &cells, CellType cell_type);
};

}
