// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/indexproperties.h>

namespace search::attribute {

/**
 * Parameters for attribute blueprints from rank profile and query.
 */
struct AttributeBlueprintParams
{
    double global_filter_lower_limit;
    double global_filter_upper_limit;

    AttributeBlueprintParams(double global_filter_lower_limit_in,
                             double global_filter_upper_limit_in)
        : global_filter_lower_limit(global_filter_lower_limit_in),
          global_filter_upper_limit(global_filter_upper_limit_in)
    {
    }

    AttributeBlueprintParams()
        : AttributeBlueprintParams(fef::indexproperties::matching::GlobalFilterLowerLimit::DEFAULT_VALUE,
                                   fef::indexproperties::matching::GlobalFilterUpperLimit::DEFAULT_VALUE)
    {
    }
};

}
