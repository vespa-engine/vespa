// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_collection_spec.h"
#include <vespa/searchcore/proton/common/alloc_strategy.h>
#include <vespa/searchlib/common/serialnum.h>

namespace vespa::config::search::internal { class InternalAttributesType; };

namespace proton {

/**
 * A factory for generating an AttributeCollectionSpec based on AttributesConfig
 * from the config server.
 */
class AttributeCollectionSpecFactory
{
private:
    using AttributesConfig = const vespa::config::search::internal::InternalAttributesType;

    const AllocStrategy        _alloc_strategy;
    const bool                 _fastAccessOnly;

public:
    AttributeCollectionSpecFactory(const AllocStrategy& alloc_strategy, bool fastAccessOnly);
    ~AttributeCollectionSpecFactory();

    std::unique_ptr<AttributeCollectionSpec> create(const AttributesConfig &attrCfg, uint32_t docIdLimit, search::SerialNum serialNum) const;
};

} // namespace proton

