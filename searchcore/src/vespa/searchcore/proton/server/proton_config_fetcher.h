// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bootstrapconfigmanager.h"
#include "i_document_db_config_owner.h"
#include <vespa/fastos/thread.h>
#include <vespa/searchcore/proton/common/doctypename.h>
#include <vespa/config/retriever/configretriever.h>
#include <vespa/config/subscription/configuri.h>
#include <deque>

class FNET_Transport;

namespace document { class DocumentTypeRepo; }

namespace proton {

class BootstrapConfig;
class DocumentDBConfigManager;
class IProtonConfigurer;

/**
 * A ProtonConfigFetcher monitors all config in proton and document dbs for change
 * and starts proton reconfiguration if config has been reloaded.
 */
class ProtonConfigFetcher : public FastOS_Runnable
{
public:
    using BootstrapConfigSP = std::shared_ptr<BootstrapConfig>;

    ProtonConfigFetcher(FNET_Transport & transport, const config::ConfigUri & configUri, IProtonConfigurer &owner, vespalib::duration subscribeTimeout);
    ~ProtonConfigFetcher() override;
    /**
     * Get the current config generation.
     */
    int64_t getGeneration() const;

    /**
     * Start config fetcher, callbacks may come from now on.
     */
    void start(FastOS_ThreadPool & threadPool);

    /**
     * Shutdown config fetcher, ensuring that no more callbacks arrive
     */
    void close();

    void Run(FastOS_ThreadInterface * thread, void *arg) override;

private:
    typedef std::map<DocTypeName, std::shared_ptr<DocumentDBConfigManager>> DBManagerMap;
    using OldDocumentTypeRepo = std::pair<vespalib::steady_time, std::shared_ptr<const document::DocumentTypeRepo>>;
    using lock_guard = std::lock_guard<std::mutex>;


    FNET_Transport          & _transport;
    BootstrapConfigManager    _bootstrapConfigManager;
    config::ConfigRetriever   _retriever;
    IProtonConfigurer       & _owner;

    mutable std::mutex        _mutex; // Protects maps
    std::condition_variable   _cond;
    DBManagerMap              _dbManagerMap;
    bool                      _running;

    std::deque<OldDocumentTypeRepo>                   _oldDocumentTypeRepos;
    std::shared_ptr<const document::DocumentTypeRepo> _currentDocumentTypeRepo;

    void fetchConfigs();
    void updateDocumentDBConfigs(const BootstrapConfigSP & config, const config::ConfigSnapshot & snapshot);
    void reconfigure();
    const config::ConfigKeySet pruneManagerMap(const BootstrapConfigSP & config);
    void rememberDocumentTypeRepo(std::shared_ptr<const document::DocumentTypeRepo> repo);
};

} // namespace proton
