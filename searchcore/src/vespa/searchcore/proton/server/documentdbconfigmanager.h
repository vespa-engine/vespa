// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/config/config.h>
#include "bootstrapconfig.h"
#include "documentdbconfig.h"

namespace proton {


/**
 * This class manages the subscription for documentdb configs.
 */
class DocumentDBConfigManager
{
public:
    typedef std::shared_ptr<DocumentDBConfigManager> SP;

private:
    vespalib::string     _configId;
    vespalib::string     _docTypeName;
    BootstrapConfig::SP  _bootstrapConfig;
    DocumentDBConfig::SP _pendingConfigSnapshot;
    bool                 _ignoreForwardedConfig;
    vespalib::Lock       _pendingConfigLock;
    config::ConfigKeySet _extraConfigKeys;

    search::index::Schema::SP
    buildNewSchema(const vespa::config::search::AttributesConfig & newAttributesConfig,
                   const vespa::config::search::SummaryConfig & newSummaryConfig,
                   const vespa::config::search::IndexschemaConfig & newIndexschemaConfig);

    search::index::Schema::SP
    buildSchema(const vespa::config::search::AttributesConfig & newAttributesConfig,
                const vespa::config::search::SummaryConfig & newSummaryConfig,
                const vespa::config::search::IndexschemaConfig & newIndexschemaConfig);
public:
    DocumentDBConfigManager(const vespalib::string &configId,
                            const vespalib::string &docTypeName);
    void update(const config::ConfigSnapshot & snapshot);

    DocumentDBConfig::SP
    getConfig() const {
        vespalib::LockGuard lock(_pendingConfigLock);
        return _pendingConfigSnapshot;
    }

    void forwardConfig(const BootstrapConfig::SP & config);
    const config::ConfigKeySet createConfigKeySet(void) const;
    void setExtraConfigKeys(const config::ConfigKeySet & extraConfigKeys) { _extraConfigKeys = extraConfigKeys; }
    const config::ConfigKeySet & getExtraConfigKeys() const { return _extraConfigKeys; }
    const vespalib::string & getConfigId() const { return _configId; }
};

/**
 * Simple helper class to use a config holder in tests and fileconfig manager.
 */
class DocumentDBConfigHelper
{
public:
    DocumentDBConfigHelper(const config::DirSpec &spec,
                           const vespalib::string &docTypeName,
                           const config::ConfigKeySet &extraConfigKeys = config::ConfigKeySet())
        : _mgr("", docTypeName),
          _retriever()
    {
        _mgr.setExtraConfigKeys(extraConfigKeys);
        _retriever.reset(new config::ConfigRetriever(_mgr.createConfigKeySet(),
                                                     config::IConfigContext::SP(new config::ConfigContext(spec))));
    }

    bool
    nextGeneration(int timeoutInMillis)
    {
        config::ConfigSnapshot
            snapshot(_retriever->getBootstrapConfigs(timeoutInMillis));
        if (snapshot.empty())
            return false;
        _mgr.update(snapshot);
        return true;
    }

    DocumentDBConfig::SP
    getConfig(void) const
    {
        return _mgr.getConfig();
    }

    void
    forwardConfig(const BootstrapConfig::SP & config)
    {
        _mgr.forwardConfig(config);
    }
private:
    DocumentDBConfigManager _mgr;
    std::unique_ptr<config::ConfigRetriever> _retriever;
};

} // namespace proton

