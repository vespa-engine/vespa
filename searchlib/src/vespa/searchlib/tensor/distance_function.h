// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vespa/eval/eval/cell_type.h>

namespace vespalib::eval { struct TypedCells; }

namespace search::tensor {

class DistanceConverter {
public:
    virtual ~DistanceConverter() = default;

    // convert threshold (external distance units) to internal units
    virtual double convert_threshold(double threshold) const = 0;

    // convert internal distance to rawscore (1.0 / (1.0 + d))
    virtual double to_rawscore(double distance) const = 0;
};

}
