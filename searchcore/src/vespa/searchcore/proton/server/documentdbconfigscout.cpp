// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentdbconfigscout.h"
#include "documentdbconfig.h"
#include <vespa/searchcore/proton/attribute/attributesconfigscout.h>

using vespa::config::search::AttributesConfig;

namespace proton {

std::shared_ptr<DocumentDBConfig>
DocumentDBConfigScout::scout(const std::shared_ptr<DocumentDBConfig> &config,
                             const DocumentDBConfig &liveConfig)
{
    AttributesConfigScout acScout(liveConfig.getAttributesConfig());
    std::shared_ptr<AttributesConfig> ac(acScout.adjust(config->getAttributesConfig()));
    if (*ac == config->getAttributesConfig())
        return config; // no change
    return config->newFromAttributesConfig(ac);
}


} // namespace proton
