// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "configvalidator.h"

namespace proton {

/**
 * Class used to validate new attribute config before starting using it.
 **/
struct AttributeConfigValidator
{
    static ConfigValidator::Result
    validate(const vespa::config::search::AttributesConfig &newCfg,
             const vespa::config::search::AttributesConfig &oldCfg);
};

} // namespace proton

