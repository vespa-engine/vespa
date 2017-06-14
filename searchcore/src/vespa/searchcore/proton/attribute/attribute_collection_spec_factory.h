// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_collection_spec.h"
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchcommon/common/growstrategy.h>
#include <vespa/searchlib/common/serialnum.h>
#include <vespa/config-attributes.h>

namespace proton {

/**
 * A factory for generating an AttributeCollectionSpec based on AttributesConfig
 * from the config server.
 */
class AttributeCollectionSpecFactory
{
private:
    typedef vespa::config::search::AttributesConfig AttributesConfig;

    const search::GrowStrategy _growStrategy;
    const size_t               _growNumDocs;
    const bool                 _fastAccessOnly;

public:
    AttributeCollectionSpecFactory(const search::GrowStrategy &growStrategy,
                                   size_t growNumDocs,
                                   bool fastAccessOnly);

    AttributeCollectionSpec::UP create(const AttributesConfig &attrCfg,
                                       uint32_t docIdLimit,
                                       search::SerialNum serialNum) const;
};

} // namespace proton

