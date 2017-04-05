// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "documentdbconfigscout.h"
#include <vespa/searchcore/proton/attribute/attributesconfigscout.h>
#include <vespa/searchcore/proton/attribute/attribute_specs_builder.h>

using vespa::config::search::AttributesConfig;

namespace proton
{


DocumentDBConfig::SP
DocumentDBConfigScout::scout(const DocumentDBConfig::SP &config,
                             const DocumentDBConfig &liveConfig)
{
    AttributesConfigScout acScout(liveConfig.getAttributesConfig());
    std::shared_ptr<AttributesConfig>
        ac(acScout.adjust(config->getAttributesConfig()));
    if (*ac == config->getAttributesConfig())
        return config; // no change
    AttributeSpecsBuilder attributeSpecsBuilder;
    attributeSpecsBuilder.setup(*ac);
    return config->newFromAttributesConfig(attributeSpecsBuilder.getAttributesConfig(),
                                           attributeSpecsBuilder.getAttributeSpecs());
}


} // namespace proton
