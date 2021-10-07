// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::attribute {

/**
 * Parameters for attribute blueprints from rank profile and query.
 */
struct AttributeBlueprintParams
{
    double nearest_neighbor_brute_force_limit;
    
    AttributeBlueprintParams(double nearest_neighbor_brute_force_limit_in)
        : nearest_neighbor_brute_force_limit(nearest_neighbor_brute_force_limit_in)
    {
    }

    AttributeBlueprintParams()
        : AttributeBlueprintParams(0.05)
    {
    }
};

}
