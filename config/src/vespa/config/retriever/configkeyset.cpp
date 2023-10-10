// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configkeyset.h"

namespace config {

ConfigKeySet &
ConfigKeySet::add(const ConfigKeySet & configKeySet)
{
    insert(configKeySet.begin(), configKeySet.end());
    return *this;
}

}
