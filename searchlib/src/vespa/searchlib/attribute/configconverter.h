// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributevector.h"
#include <vespa/config-attributes.h>

namespace search {
namespace attribute {

/**
 * Class used to convert from attributes config to the config used by the AttributeVector implementation.
 **/
class ConfigConverter {
public:
    static Config convert(const vespa::config::search::AttributesConfig::Attribute & cfg);
};

} // namespace attribute
} // namespace search

