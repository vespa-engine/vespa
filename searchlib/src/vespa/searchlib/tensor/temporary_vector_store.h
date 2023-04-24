// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vespa/eval/eval/cell_type.h>
#include <vespa/eval/eval/typed_cells.h>
#include <vespa/vespalib/util/arrayref.h>

namespace search::tensor {

/** helper class - temporary storage of possibly-converted vector cells */
template <typename FloatType>
class TemporaryVectorStore {
private:
    std::vector<FloatType> _tmpSpace;
    vespalib::ConstArrayRef<FloatType> internal_convert(vespalib::eval::TypedCells cells, size_t offset);
public:
    TemporaryVectorStore(size_t vectorSize) : _tmpSpace(vectorSize * 2) {}
    vespalib::ConstArrayRef<FloatType> storeLhs(vespalib::eval::TypedCells cells) {
        return internal_convert(cells, 0);
    }
    vespalib::ConstArrayRef<FloatType> convertRhs(vespalib::eval::TypedCells cells) {
        if (vespalib::eval::get_cell_type<FloatType>() == cells.type) [[likely]] {
            return cells.unsafe_typify<FloatType>();
        } else {
            return internal_convert(cells, cells.size);
        }
    }
};

}
