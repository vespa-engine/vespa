// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "distance_function.h"
#include "distance_function_factory.h"
#include <vespa/eval/eval/typed_cells.h>
#include <vespa/vespalib/hwaccelrated/iaccelrated.h>
#include <vespa/vespalib/util/typify.h>
#include <cmath>

namespace search::tensor {

/**
 * Calculates great-circle distance between Latitude/Longitude pairs,
 * where input is given as degrees.
 * Output distance is measured in kilometers.
 **/
class GeoDistanceFunctionFactory : public DistanceFunctionFactory {
public:
    GeoDistanceFunctionFactory() = default;
    BoundDistanceFunction::UP for_query_vector(const vespalib::eval::TypedCells& lhs) override;
    BoundDistanceFunction::UP for_insertion_vector(const vespalib::eval::TypedCells& lhs) override;
};

}
