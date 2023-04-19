// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distance_function_factory.h"
#include "distance_functions.h"
#include <vespa/vespalib/util/typify.h>
#include <vespa/vespalib/util/array.h>
#include <vespa/vespalib/util/arrayref.h>
#include <vespa/log/log.h>

LOG_SETUP(".searchlib.tensor.distance_function_factory");

using search::attribute::DistanceMetric;
using vespalib::eval::CellType;
using vespalib::eval::ValueType;

namespace search::tensor {

DistanceFunction::UP
make_distance_function(DistanceMetric variant, CellType cell_type)
{
    switch (variant) {
    case DistanceMetric::Euclidean:
        switch (cell_type) {
        case CellType::FLOAT:  return std::make_unique<SquaredEuclideanDistanceHW<float>>();
        case CellType::DOUBLE: return std::make_unique<SquaredEuclideanDistanceHW<double>>();
        case CellType::INT8:   return std::make_unique<SquaredEuclideanDistanceHW<vespalib::eval::Int8Float>>();
        default:               return std::make_unique<SquaredEuclideanDistance>(CellType::FLOAT);
        }
    case DistanceMetric::Angular:
        switch (cell_type) {
        case CellType::FLOAT:  return std::make_unique<AngularDistanceHW<float>>();
        case CellType::DOUBLE: return std::make_unique<AngularDistanceHW<double>>();
        default:               return std::make_unique<AngularDistance>(CellType::FLOAT);
        }
    case DistanceMetric::GeoDegrees:
        return std::make_unique<GeoDegreesDistance>(CellType::DOUBLE);
    case DistanceMetric::InnerProduct:
        switch (cell_type) {
        case CellType::FLOAT:  return std::make_unique<InnerProductDistanceHW<float>>();
        case CellType::DOUBLE: return std::make_unique<InnerProductDistanceHW<double>>();
        default:               return std::make_unique<InnerProductDistance>(CellType::FLOAT);
        }
    case DistanceMetric::Hamming:
        return std::make_unique<HammingDistance>(cell_type);
    }
    // not reached:
    return DistanceFunction::UP();
}


class SimpleBoundDistanceFunction : public BoundDistanceFunction {
    const vespalib::eval::TypedCells _lhs;
    const DistanceFunction *_df;
public:
    SimpleBoundDistanceFunction(const vespalib::eval::TypedCells& lhs,
                                const DistanceFunction *df)
        : BoundDistanceFunction(lhs.type),
          _lhs(lhs),
          _df(df)
        {}

    double calc(const vespalib::eval::TypedCells& rhs) const override {
        return _df->calc(_lhs, rhs);
    }
    double convert_threshold(double threshold) const override {
        return _df->convert_threshold(threshold);
    }
    double to_rawscore(double distance) const override {
        return _df->to_rawscore(distance);
    }
    double calc_with_limit(const vespalib::eval::TypedCells& rhs, double limit) const override {
        return _df->calc_with_limit(_lhs, rhs, limit);
    }
};

class SimpleDistanceFunctionFactory : public DistanceFunctionFactory {
    DistanceFunction::UP _df;
public:
    SimpleDistanceFunctionFactory(DistanceFunction::UP df)
        : DistanceFunctionFactory(df->expected_cell_type()),
          _df(std::move(df))
        {}

    BoundDistanceFunction::UP forQueryVector(const vespalib::eval::TypedCells& lhs) override {
        return std::make_unique<SimpleBoundDistanceFunction>(lhs, _df.get());
    }
    BoundDistanceFunction::UP forInsertionVector(const vespalib::eval::TypedCells& lhs) override {
        return std::make_unique<SimpleBoundDistanceFunction>(lhs, _df.get());
    }
};

std::unique_ptr<DistanceFunctionFactory>
make_distance_function_factory(search::attribute::DistanceMetric variant,
                               vespalib::eval::CellType cell_type)
{
    auto df = make_distance_function(variant, cell_type);
    return std::make_unique<SimpleDistanceFunctionFactory>(std::move(df));
}

}
