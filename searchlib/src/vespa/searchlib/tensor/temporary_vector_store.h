// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/typed_cells.h>

namespace search::tensor {

/** helper class - temporary storage of possibly-converted vector cells */
template <typename FloatType>
class TemporaryVectorStore {
private:
    using TypedCells = vespalib::eval::TypedCells;
    std::vector<FloatType> _tmpSpace;
    vespalib::ConstArrayRef<FloatType> internal_convert(TypedCells cells, size_t offset) noexcept;
public:
    explicit TemporaryVectorStore(size_t vectorSize) noexcept : _tmpSpace(vectorSize * 2) {}
    vespalib::ConstArrayRef<FloatType> storeLhs(TypedCells cells) noexcept {
        return internal_convert(cells, 0);
    }
    vespalib::ConstArrayRef<FloatType> convertRhs(TypedCells cells) {
        if (vespalib::eval::get_cell_type<FloatType>() == cells.type) [[likely]] {
            return cells.unsafe_typify<FloatType>();
        } else {
            return internal_convert(cells, cells.size);
        }
    }
};

}
