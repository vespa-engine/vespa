// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "documentdbconfig.h"
#include <mutex>

class FNET_Transport;

namespace config {
    class ConfigRetriever;
    class DirSpec;
}
namespace proton {

class BootstrapConfig;

/**
 * This class manages the subscription for documentdb configs.
 */
class DocumentDBConfigManager
{
public:
    using SP = std::shared_ptr<DocumentDBConfigManager>;
    using BootstrapConfigSP = std::shared_ptr<BootstrapConfig>;

private:
    vespalib::string     _configId;
    vespalib::string     _docTypeName;
    BootstrapConfigSP    _bootstrapConfig;
    DocumentDBConfig::SP _pendingConfigSnapshot;
    bool                 _ignoreForwardedConfig;
    mutable std::mutex   _pendingConfigMutex;

    search::index::Schema::SP
    buildSchema(const DocumentDBConfig::AttributesConfig & newAttributesConfig,
                const DocumentDBConfig::IndexschemaConfig & newIndexschemaConfig);

public:
    DocumentDBConfigManager(const vespalib::string &configId, const vespalib::string &docTypeName);
    ~DocumentDBConfigManager();
    void update(FNET_Transport & transport, const config::ConfigSnapshot & snapshot);

    DocumentDBConfig::SP getConfig() const;

    void forwardConfig(const BootstrapConfigSP & config);
    const config::ConfigKeySet createConfigKeySet() const;
    const vespalib::string & getConfigId() const { return _configId; }
};

/**
 * Simple helper class to use a config holder in tests and fileconfig manager.
 */
class DocumentDBConfigHelper
{
public:
    DocumentDBConfigHelper(const config::DirSpec &spec, const vespalib::string &docTypeName);
    ~DocumentDBConfigHelper();

    bool nextGeneration(FNET_Transport & transport, vespalib::duration timeout);
    DocumentDBConfig::SP getConfig() const;
    void forwardConfig(const std::shared_ptr<BootstrapConfig> & config);
private:
    DocumentDBConfigManager _mgr;
    std::unique_ptr<config::ConfigRetriever> _retriever;
};

} // namespace proton

