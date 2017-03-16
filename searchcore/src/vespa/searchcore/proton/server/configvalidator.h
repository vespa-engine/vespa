// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "config_validator_result_type.h"
#include "config_validator_result.h"
#include <vespa/searchcommon/common/schema.h>
#include <vespa/config-attributes.h>

namespace proton {

/**
 * Class used to validate new document db config before starting using it.
 **/
class ConfigValidator {
public:
    class Config
    {
    private:
        const search::index::Schema &_schema;
        const vespa::config::search::AttributesConfig &_attributeCfg;
    public:
        Config(const search::index::Schema &schema,
               const vespa::config::search::AttributesConfig &attributeCfg)
            : _schema(schema),
              _attributeCfg(attributeCfg)
        {}
        const search::index::Schema &getSchema() const {
            return _schema;
        }
        const vespa::config::search::AttributesConfig &getAttributeConfig() const {
            return _attributeCfg;
        }
    };

    /**
     * Check if new schema can be applied or not.
     */
    static configvalidator::Result
    validate(const Config &newCfg,
             const Config &oldCfg,
             const search::index::Schema &oldHistory);
};

} // namespace proton

