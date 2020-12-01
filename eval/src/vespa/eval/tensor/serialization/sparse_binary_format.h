// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vespa/eval/eval/value_type.h>

namespace vespalib { class nbostream; }

namespace vespalib::tensor {

class Tensor;

/**
 * Class for serializing a sparse tensor.
 */
class SparseBinaryFormat
{
public:
    using CellType = eval::CellType;

    static void serialize(nbostream &stream, const Tensor &tensor);
    static std::unique_ptr<Tensor> deserialize(nbostream &stream, CellType cell_type);
};

}
