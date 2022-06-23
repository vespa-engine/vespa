// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/cell_type.h>
#include <vespa/eval/eval/typed_cells.h>
#include <vespa/eval/eval/value_type.h>
#include <cstring>

namespace search::tensor {

/**
 * Comparator used to compare two vespalib::eval::TypedCells instances.
 *
 * The caller must first validate that they are of the same vespalib::eval::ValueType.
 */
class TypedCellsComparator {
private:
    size_t _mem_size;

public:
    TypedCellsComparator(const vespalib::eval::ValueType& type)
        : _mem_size(vespalib::eval::CellTypeUtils::mem_size(type.cell_type(), type.dense_subspace_size()))
    {}
    bool equals(const vespalib::eval::TypedCells& lhs, const vespalib::eval::TypedCells& rhs) const {
        return std::memcmp(lhs.data, rhs.data, _mem_size) == 0;
    }
};

}
