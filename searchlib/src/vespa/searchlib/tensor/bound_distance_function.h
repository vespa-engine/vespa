// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vespa/eval/eval/cell_type.h>
#include <vespa/eval/eval/typed_cells.h>
#include <vespa/vespalib/util/array.h>
#include <vespa/vespalib/util/arrayref.h>
#include "distance_function.h"

namespace vespalib::eval { struct TypedCells; }

namespace search::tensor {

/**
 * Interface used to calculate the distance from a prebound n-dimensional vector.
 *
 * Use from a single thread only - not required to be thread safe.
 * The actual implementation may keep state about the prebound vector and
 * mutable temporary storage.
 */
class BoundDistanceFunction : public DistanceConverter {
private:
    vespalib::eval::CellType _expect_cell_type;
public:
    using UP = std::unique_ptr<BoundDistanceFunction>;

    BoundDistanceFunction(vespalib::eval::CellType expected) : _expect_cell_type(expected) {}

    virtual ~BoundDistanceFunction() = default;

    // input vectors will be converted to this cell type:
    vespalib::eval::CellType expected_cell_type() const {
        return _expect_cell_type;
    }

    // calculate internal distance (comparable)
    virtual double calc(const vespalib::eval::TypedCells& rhs) const = 0;

    // calculate internal distance, early return allowed if > limit
    virtual double calc_with_limit(const vespalib::eval::TypedCells& rhs,
                                   double limit) const = 0;
};


/** helper class - temporary storage of possibly-converted vector cells */
template <typename FloatType>
class TemporaryVectorStore {
private:
    vespalib::Array<FloatType> _tmpSpace;
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

template<typename FloatType>
class ConvertingBoundDistance : public BoundDistanceFunction {
    mutable TemporaryVectorStore<FloatType> _tmpSpace;
    const vespalib::eval::TypedCells _lhs;
    const DistanceFunction &_df;
public:
    ConvertingBoundDistance(const vespalib::eval::TypedCells& lhs, const DistanceFunction &df)
        : BoundDistanceFunction(vespalib::eval::get_cell_type<FloatType>()),
          _tmpSpace(lhs.size),
          _lhs(_tmpSpace.storeLhs(lhs)),
          _df(df)
    {}
    double calc(const vespalib::eval::TypedCells& rhs) const override {
        return _df.calc(_lhs, vespalib::eval::TypedCells(_tmpSpace.convertRhs(rhs)));
    }
    double convert_threshold(double threshold) const override {
        return _df.convert_threshold(threshold);
    }
    double to_rawscore(double distance) const override {
        return _df.to_rawscore(distance);
    }
    double calc_with_limit(const vespalib::eval::TypedCells& rhs, double limit) const override {
        return _df.calc_with_limit(_lhs, vespalib::eval::TypedCells(_tmpSpace.convertRhs(rhs)), limit);
    }
};

}
