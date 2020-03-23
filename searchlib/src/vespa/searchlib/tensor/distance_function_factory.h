// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "distance_function.h"
#include <vespa/eval/eval/value_type.h>
#include <vespa/searchcommon/attribute/distance_metric.h>

namespace search::tensor {

DistanceFunction::UP
make_distance_function(search::attribute::DistanceMetric variant,
                       vespalib::eval::ValueType::CellType cell_type);

}
