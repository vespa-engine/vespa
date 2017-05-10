// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fastos/thread.h>
#include <vespa/searchcore/proton/common/doctypename.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/config/config.h>
#include "bootstrapconfigmanager.h"
#include "documentdbconfigmanager.h"
#include "i_document_db_config_owner.h"

namespace proton {

class BootstrapConfig;
class IProtonConfigurer;

/**
 * A ProtonConfigFetcher monitors all config in proton and document dbs for change
 * and starts proton reconfiguration if config has been reloaded.
 */
class ProtonConfigFetcher : public FastOS_Runnable
{
public:
    using BootstrapConfigSP = std::shared_ptr<BootstrapConfig>;

    ProtonConfigFetcher(const config::ConfigUri & configUri, IProtonConfigurer &owner, uint64_t subscribeTimeout);
    ~ProtonConfigFetcher();
    /**
     * Get the current config generation.
     */
    int64_t getGeneration() const;

    /**
     * Start config fetcher, callbacks may come from now on.
     */
    void start();

    /**
     * Shutdown config fetcher, ensuring that no more callbacks arrive
     */
    void close();

    DocumentDBConfig::SP getDocumentDBConfig(const DocTypeName & docTypeName) const;

    void Run(FastOS_ThreadInterface * thread, void *arg) override;

private:
    typedef std::map<DocTypeName, DocumentDBConfigManager::SP> DBManagerMap;

    BootstrapConfigManager _bootstrapConfigManager;
    config::ConfigRetriever _retriever;
    IProtonConfigurer & _owner;

    mutable std::mutex _mutex; // Protects maps
    using lock_guard = std::lock_guard<std::mutex>;
    DBManagerMap _dbManagerMap;

    FastOS_ThreadPool _threadPool;

    void fetchConfigs();
    void updateDocumentDBConfigs(const BootstrapConfigSP & config, const config::ConfigSnapshot & snapshot);
    void reconfigure();
    const config::ConfigKeySet pruneManagerMap(const BootstrapConfigSP & config);
};


} // namespace proton

