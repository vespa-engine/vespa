// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_collection_spec.h"
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchcommon/common/growstrategy.h>
#include <vespa/searchlib/common/serialnum.h>

namespace proton {

class AttributeSpecs;

/**
 * A factory for generating an AttributeCollectionSpec based on AttributesConfig
 * from the config server.
 */
class AttributeCollectionSpecFactory
{
private:
    const search::GrowStrategy _growStrategy;
    const size_t               _growNumDocs;
    const bool                 _fastAccessOnly;

public:
    AttributeCollectionSpecFactory(const search::GrowStrategy &growStrategy,
                                   size_t growNumDocs,
                                   bool fastAccessOnly);

    AttributeCollectionSpec::UP create(const AttributeSpecs &attrSpecs,
                                       uint32_t docIdLimit,
                                       search::SerialNum serialNum) const;
};

} // namespace proton

