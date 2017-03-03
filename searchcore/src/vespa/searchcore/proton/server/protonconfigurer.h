// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fastos/thread.h>
#include <vespa/searchcore/proton/common/doctypename.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/config/config.h>
#include "bootstrapconfigmanager.h"
#include "documentdbconfigmanager.h"

namespace proton {

class BootstrapConfig;

class IBootstrapOwner
{
public:
    virtual ~IBootstrapOwner() { }
    virtual void reconfigure(const std::shared_ptr<BootstrapConfig> & config) = 0;
};

class IDocumentDBConfigOwner
{
public:
    virtual ~IDocumentDBConfigOwner() { }
    virtual void reconfigure(const DocumentDBConfig::SP & config) = 0;
};

/**
 * A ProtonConfigurer monitors all config in proton and document dbs for change
 * and starts proton reconfiguration if config has been reloaded.
 */
class ProtonConfigurer : public FastOS_Runnable
{
public:
    using BootstrapConfigSP = std::shared_ptr<BootstrapConfig>;

    ProtonConfigurer(const config::ConfigUri & configUri, IBootstrapOwner * owner, uint64_t subscribeTimeout);
    ~ProtonConfigurer();
    /**
     * Register a new document db that should receive config updates.
     */
    void registerDocumentDB(const DocTypeName & docTypeName, IDocumentDBConfigOwner * owner);

    /**
     * Remove document db from registry, ensuring that no callbacks will come
     * after this method has returned.
     */
    void unregisterDocumentDB(const DocTypeName & docTypeName);

    /**
     * Get the current config generation.
     */
    int64_t getGeneration() const;

    /**
     * Start configurer, callbacks may come from now on.
     */
    void start();

    /**
     * Shutdown configurer, ensuring that no more callbacks arrive
     */
    void close();

    DocumentDBConfig::SP getDocumentDBConfig(const DocTypeName & docTypeName) const;

    void Run(FastOS_ThreadInterface * thread, void *arg);

private:
    typedef std::map<DocTypeName, IDocumentDBConfigOwner * > DocumentDBOwnerMap;
    typedef std::map<DocTypeName, DocumentDBConfigManager::SP> DBManagerMap;

    BootstrapConfigManager _bootstrapConfigManager;
    config::ConfigRetriever _retriever;
    IBootstrapOwner * _bootstrapOwner;

    vespalib::Lock _lock; // Protects maps
    DBManagerMap _dbManagerMap;
    DocumentDBOwnerMap _documentDBOwnerMap;

    FastOS_ThreadPool _threadPool;

    void fetchConfigs();
    void reconfigureBootstrap(const config::ConfigSnapshot & snapshot);
    void updateDocumentDBConfigs(const BootstrapConfigSP & config, const config::ConfigSnapshot & snapshot);
    void reconfigureDocumentDBs();
    const config::ConfigKeySet pruneManagerMap(const BootstrapConfigSP & config);
};


} // namespace proton

