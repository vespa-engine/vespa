// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/cell_type.h>

namespace vespalib::eval { class ValueType; }

namespace search::tensor {

/*
 * Class describing the type of a dense subspace in a tensor.
 */
class SubspaceType
{
    vespalib::eval::CellType _cell_type;
    size_t                   _size;     // # cells
    size_t                   _mem_size; // # bytes
public:
    explicit SubspaceType(const vespalib::eval::ValueType& type);
    vespalib::eval::CellType cell_type() const noexcept { return _cell_type; }
    size_t size() const noexcept { return _size; }
    size_t mem_size() const noexcept { return _mem_size; }
};

}
