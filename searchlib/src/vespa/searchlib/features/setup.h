// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/iblueprintregistry.h>

namespace search::features {

/**
 * Adds prototypes for all features in this library to the given registry.
 *
 * @param registry The blueprint registry to add prototypes to.
 **/
void setup_search_features(fef::IBlueprintRegistry & registry);

}
