// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/typed_cells.h>

namespace search::tensor {

/**
 * Helper class containing temporary memory storage for possibly converted vector cells.
 */
template <typename FloatTypeT>
class TemporaryVectorStore {
public:
    using FloatType = FloatTypeT;
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

/**
 * Helper class used when TypedCells vector memory is just referenced,
 * and used directly in calculations without any transforms.
 */
template <typename FloatTypeT>
class ReferenceVectorStore {
public:
    using FloatType = FloatTypeT;
private:
    using TypedCells = vespalib::eval::TypedCells;
public:
    explicit ReferenceVectorStore(size_t vector_size) noexcept { (void) vector_size; }
    vespalib::ConstArrayRef<FloatType> storeLhs(TypedCells cells) noexcept {
        return cells.unsafe_typify<FloatType>();
    }
    vespalib::ConstArrayRef<FloatType> convertRhs(TypedCells cells) noexcept {
        return cells.unsafe_typify<FloatType>();
    }
};

}
