// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distance_calculator.h"
#include "distance_function_factory.h"
#include "nearest_neighbor_index.h"
#include <vespa/eval/eval/fast_value.h>
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::IllegalArgumentException;
using vespalib::eval::CellType;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::TypedCells;
using vespalib::eval::Value;
using vespalib::eval::ValueType;
using vespalib::make_string;

namespace search::tensor {

DistanceCalculator::DistanceCalculator(const tensor::ITensorAttribute& attr_tensor,
                                       const vespalib::eval::Value& query_tensor_in)
    : _attr_tensor(attr_tensor),
      _query_tensor(&query_tensor_in),
      _dist_fun()
{
    auto * nns_index = _attr_tensor.nearest_neighbor_index();
    auto & dff = nns_index ? nns_index->distance_function_factory() : attr_tensor.distance_function_factory();
    _dist_fun = dff.for_query_vector(query_tensor_in.cells());
    assert(_dist_fun);
}

DistanceCalculator::DistanceCalculator(const tensor::ITensorAttribute& attr_tensor,
                                       BoundDistanceFunction::UP function_in)
    : _attr_tensor(attr_tensor),
      _query_tensor(nullptr),
      _dist_fun(std::move(function_in))
{
}

DistanceCalculator::~DistanceCalculator() = default;

namespace {

bool
supported_tensor_type(const vespalib::eval::ValueType& type)
{
    if (type.is_dense() && type.dimensions().size() == 1) {
        return true;
    }
    if (type.is_mixed() && type.dimensions().size() == 2) {
        return true;
    }
    return false;
}

bool
is_compatible(const vespalib::eval::ValueType& lhs,
              const vespalib::eval::ValueType& rhs)
{
    return (lhs.indexed_dimensions() == rhs.indexed_dimensions());
}

}

std::unique_ptr<DistanceCalculator>
DistanceCalculator::make_with_validation(const search::attribute::IAttributeVector& attr,
                                         const vespalib::eval::Value& query_tensor_in)
{
    const ITensorAttribute* attr_tensor = attr.asTensorAttribute();
    if (attr_tensor == nullptr) {
        throw IllegalArgumentException("Attribute is not a tensor");
    }
    const auto& at_type = attr_tensor->getTensorType();
    if (!supported_tensor_type(at_type)) {
        throw IllegalArgumentException(make_string("Attribute tensor type (%s) is not supported",
                                                   at_type.to_spec().c_str()));
    }
    const auto& qt_type = query_tensor_in.type();
    if (!qt_type.is_dense()) {
        throw IllegalArgumentException(make_string("Query tensor type (%s) is not a dense tensor",
                                                   qt_type.to_spec().c_str()));
    }
    if (!is_compatible(at_type, qt_type)) {
        throw IllegalArgumentException(make_string("Attribute tensor type (%s) and query tensor type (%s) are not compatible",
                                                   at_type.to_spec().c_str(), qt_type.to_spec().c_str()));
    }
    return std::make_unique<DistanceCalculator>(*attr_tensor, query_tensor_in);
}

}

