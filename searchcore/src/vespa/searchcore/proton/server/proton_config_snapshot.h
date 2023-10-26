// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/common/doctypename.h>
#include <map>
#include <memory>

namespace proton {

class BootstrapConfig;
class DocumentDBConfig;

/*
 * Class representing a config snapshot accross all document dbs as well as
 * the matching bootstrap config.
 */
class ProtonConfigSnapshot
{
    using DocumentDBConfigs = std::map<DocTypeName, std::shared_ptr<DocumentDBConfig>>;
    std::shared_ptr<BootstrapConfig> _bootstrapConfig;
    DocumentDBConfigs _documentDBConfigs;

public:
    ProtonConfigSnapshot(std::shared_ptr<BootstrapConfig> bootstrapConfig,
                         DocumentDBConfigs documentDBConfigs);
    ~ProtonConfigSnapshot();
    const std::shared_ptr<BootstrapConfig> getBootstrapConfig() const { return _bootstrapConfig; }
    const DocumentDBConfigs &getDocumentDBConfigs() const { return _documentDBConfigs; }
};

} // namespace proton
