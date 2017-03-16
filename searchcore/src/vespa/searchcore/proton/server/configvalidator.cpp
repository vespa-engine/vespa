// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.server.configvalidator");
#include "configvalidator.h"

#include "schema_config_validator.h"
#include "attribute_config_validator.h"

using proton::configvalidator::Result;

namespace proton {

Result
ConfigValidator::validate(const ConfigValidator::Config &newCfg,
                          const ConfigValidator::Config &oldCfg,
                          const search::index::Schema &oldHistory)
{
    Result res;
    if (!(res = SchemaConfigValidator::validate(newCfg.getSchema(),
            oldCfg.getSchema(), oldHistory)).ok()) return res;
    if (!(res = AttributeConfigValidator::validate(newCfg.getAttributeConfig(),
            oldCfg.getAttributeConfig())).ok()) return res;
    return Result();
}

} // namespace proton
