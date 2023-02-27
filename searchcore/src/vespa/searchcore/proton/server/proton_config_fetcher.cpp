// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "proton_config_fetcher.h"
#include "bootstrapconfig.h"
#include "proton_config_snapshot.h"
#include "i_proton_configurer.h"
#include "documentdbconfigmanager.h"
#include <vespa/config/common/exceptions.h>
#include <vespa/vespalib/util/exceptions.h>
#include <thread>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.proton_config_fetcher");

using namespace vespa::config::search;
using namespace vespa::config::search::core;
using namespace config;
using namespace std::chrono_literals;

namespace proton {

ProtonConfigFetcher::ProtonConfigFetcher(FNET_Transport & transport, const config::ConfigUri & configUri, IProtonConfigurer &owner, vespalib::duration subscribeTimeout)
    : _transport(transport),
      _bootstrapConfigManager(configUri.getConfigId()),
      _retriever(_bootstrapConfigManager.createConfigKeySet(), configUri.getContext(), subscribeTimeout),
      _owner(owner),
      _mutex(),
      _cond(),
      _dbManagerMap(),
      _running(false),
      _thread(),
      _oldDocumentTypeRepos(),
      _currentDocumentTypeRepo()
{
}

ProtonConfigFetcher::~ProtonConfigFetcher()
{
    close();
}

void
ProtonConfigFetcher::run()
{
    while (!_retriever.isClosed()) {
        try {
            fetchConfigs();
        } catch (const config::InvalidConfigException & e) {
            LOG(warning, "Invalid config received. Ignoring and continuing with old config : %s", e.what());
            std::this_thread::sleep_for(100ms);
        }
    }
    lock_guard guard(_mutex);
    _running = false;
    _cond.notify_all();
}

const ConfigKeySet
ProtonConfigFetcher::pruneManagerMap(const BootstrapConfig::SP & config)
{
    const ProtonConfig & protonConfig = config->getProtonConfig();
    DBManagerMap newMap;
    ConfigKeySet set;

    lock_guard guard(_mutex);
    for (const ProtonConfig::Documentdb & ddb : protonConfig.documentdb) {
        DocTypeName docTypeName(ddb.inputdoctypename);
        LOG(debug, "Document type(%s), configid(%s)", ddb.inputdoctypename.c_str(), ddb.configid.c_str());
        DocumentDBConfigManager::SP mgr;
        if (_dbManagerMap.find(docTypeName) != _dbManagerMap.end()) {
            mgr = _dbManagerMap[docTypeName];
        } else {
            mgr = std::make_shared<DocumentDBConfigManager>(ddb.configid, docTypeName.getName());
        }
        set.add(mgr->createConfigKeySet());
        newMap[docTypeName] = mgr;
    }
    std::swap(_dbManagerMap, newMap);
    return set;
}

void
ProtonConfigFetcher::updateDocumentDBConfigs(const BootstrapConfig::SP & bootstrapConfig, const ConfigSnapshot & snapshot)
{
    lock_guard guard(_mutex);
    for (auto & entry : _dbManagerMap) {
        entry.second->forwardConfig(bootstrapConfig);
        entry.second->update(_transport, snapshot);
    }
}

void
ProtonConfigFetcher::reconfigure()
{
    auto bootstrapConfig = _bootstrapConfigManager.getConfig();
    int64_t generation = bootstrapConfig->getGeneration();
    std::map<DocTypeName, DocumentDBConfig::SP> dbConfigs;
    {
        lock_guard guard(_mutex);
        for (auto &kv : _dbManagerMap) {
            auto insres = dbConfigs.insert(std::make_pair(kv.first, kv.second->getConfig()));
            assert(insres.second);
            assert(insres.first->second->getGeneration() == generation);
        }
    }
    auto configSnapshot = std::make_shared<ProtonConfigSnapshot>(bootstrapConfig, std::move(dbConfigs));
    LOG(debug, "Reconfiguring proton with gen %" PRId64, generation);
    _owner.reconfigure(std::move(configSnapshot));
    LOG(debug, "Reconfigured proton with gen %" PRId64, generation);
    rememberDocumentTypeRepo(bootstrapConfig->getDocumentTypeRepoSP());
}

void
ProtonConfigFetcher::fetchConfigs()
{
    LOG(debug, "Waiting for new config generation");
    bool configured = false;
    while (!configured) {
        ConfigSnapshot bootstrapSnapshot = _retriever.getBootstrapConfigs(5000ms);
        if (_retriever.isClosed())
            return;
        LOG(debug, "Fetching snapshot");
        if (!bootstrapSnapshot.empty()) {
            _bootstrapConfigManager.update(bootstrapSnapshot);
            BootstrapConfig::SP config = _bootstrapConfigManager.getConfig();
            for (bool needsMoreConfig(true); needsMoreConfig && !_retriever.bootstrapRequired(); ) {
                const ConfigKeySet configKeySet(pruneManagerMap(config));
                // If key set is empty, we have no document databases to configure.
                // This is currently not a fatal error, so it will just try to fetch
                // the bootstrap config again.
                if (!configKeySet.empty()) {
                    ConfigSnapshot snapshot;
                    do {
                        snapshot = _retriever.getConfigs(configKeySet);
                        if (_retriever.isClosed()) {
                            return;
                        }
                    } while(snapshot.empty() && ! _retriever.bootstrapRequired());
                    if (!snapshot.empty()) {
                        LOG(debug, "Set is not empty, reconfiguring with generation %" PRId64, _retriever.getGeneration());
                        // Update document dbs first, so that we are prepared for
                        // getConfigs.
                        _bootstrapConfigManager.update(bootstrapSnapshot);
                        updateDocumentDBConfigs(config, snapshot);
                        needsMoreConfig = false;

                        // Perform callbacks
                        reconfigure();
                        configured = true;
                    }
                } else {
                    LOG(warning, "No document databases in config, trying to re-fetch bootstrap config");
                    break;
                }
            }
        }
    }
}

int64_t
ProtonConfigFetcher::getGeneration() const
{
    return _retriever.getGeneration();
}

void
ProtonConfigFetcher::start()
{
    fetchConfigs();
    lock_guard guard(_mutex);
    if (_running) return;
    _running = true;
    _thread = std::thread([this](){run();});
}

void
ProtonConfigFetcher::close()
{
    if (!_retriever.isClosed()) {
        _retriever.close();
    }
    std::unique_lock<std::mutex> guard(_mutex);
    while (_running) {
        _cond.wait(guard);
    }
    if (_thread.joinable()) {
        _thread.join();
    }
}

void
ProtonConfigFetcher::rememberDocumentTypeRepo(std::shared_ptr<const document::DocumentTypeRepo> repo)
{
    // Ensure that previous document type repo is kept alive, and also
    // any document type repo that was current within last 10 minutes.
    if (repo == _currentDocumentTypeRepo) {
        return; // no change
    }
    auto &repos = _oldDocumentTypeRepos;
    vespalib::steady_time now = vespalib::steady_clock::now();
    while (!repos.empty() && repos.front().first < now) {
        repos.pop_front();
    }
    repos.emplace_back(now + 10min, _currentDocumentTypeRepo);
    _currentDocumentTypeRepo = repo;
}

} // namespace proton
