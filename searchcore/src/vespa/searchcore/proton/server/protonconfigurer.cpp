// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "protonconfigurer.h"
#include "bootstrapconfig.h"
#include <vespa/vespalib/util/exceptions.h>
#include <thread>
#include <vespa/log/log.h>
LOG_SETUP(".proton.server.protonconfigurer");

using namespace vespa::config::search;
using namespace vespa::config::search::core;
using namespace config;
using namespace std::chrono_literals;

namespace proton {

ProtonConfigurer::ProtonConfigurer(const config::ConfigUri & configUri, IBootstrapOwner * owner, uint64_t subscribeTimeout)
    : _bootstrapConfigManager(configUri.getConfigId()),
      _retriever(_bootstrapConfigManager.createConfigKeySet(), configUri.getContext(), subscribeTimeout),
      _bootstrapOwner(owner),
      _lock(),
      _dbManagerMap(),
      _documentDBOwnerMap(),
      _threadPool(128 * 1024, 1)
{
}

ProtonConfigurer::~ProtonConfigurer()
{
    close();
}

void
ProtonConfigurer::Run(FastOS_ThreadInterface * thread, void *arg)
{
    (void) arg;
    (void) thread;
    while (!_retriever.isClosed()) {
        try {
            fetchConfigs();
        } catch (const config::InvalidConfigException & e) {
            LOG(warning, "Invalid config received. Ignoring and continuing with old config : %s", e.what());
            std::this_thread::sleep_for(100ms);
        }
    }
}

const ConfigKeySet
ProtonConfigurer::pruneManagerMap(const BootstrapConfig::SP & config)
{
    const ProtonConfig & protonConfig = config->getProtonConfig();
    DBManagerMap newMap;
    ConfigKeySet set;

    vespalib::LockGuard guard(_lock);
    for (size_t i = 0; i < protonConfig.documentdb.size(); i++) {
        const ProtonConfig::Documentdb & ddb(protonConfig.documentdb[i]);
        DocTypeName docTypeName(ddb.inputdoctypename);
        LOG(debug, "Document type(%s), configid(%s)", ddb.inputdoctypename.c_str(), ddb.configid.c_str());
        DocumentDBConfigManager::SP mgr;
        if (_dbManagerMap.find(docTypeName) != _dbManagerMap.end()) {
            mgr = _dbManagerMap[docTypeName];
        } else {
            mgr = DocumentDBConfigManager::SP(new DocumentDBConfigManager
                    (ddb.configid, docTypeName.getName()));
        }
        set.add(mgr->createConfigKeySet());
        newMap[docTypeName] = mgr;
    }
    std::swap(_dbManagerMap, newMap);
    return set;
}

void
ProtonConfigurer::reconfigureBootstrap(const ConfigSnapshot & snapshot)
{
    assert(_bootstrapOwner != NULL);
    _bootstrapConfigManager.update(snapshot);
    _bootstrapOwner->reconfigure(_bootstrapConfigManager.getConfig());
}

void
ProtonConfigurer::updateDocumentDBConfigs(const BootstrapConfig::SP & bootstrapConfig, const ConfigSnapshot & snapshot)
{
    vespalib::LockGuard guard(_lock);
    for (DBManagerMap::iterator it(_dbManagerMap.begin()), mt(_dbManagerMap.end());
         it != mt;
         it++) {
        it->second->forwardConfig(bootstrapConfig);
        it->second->update(snapshot);
    }
}

void
ProtonConfigurer::reconfigureDocumentDBs()
{
    vespalib::LockGuard guard(_lock);
    for (DocumentDBOwnerMap::iterator it(_documentDBOwnerMap.begin()), mt(_documentDBOwnerMap.end());
         it != mt;
         it++) {

        if (_dbManagerMap.find(it->first) != _dbManagerMap.end()) {
            DocumentDBConfig::SP dbConfig(_dbManagerMap[it->first]->getConfig());
            IDocumentDBConfigOwner * owner = it->second;
            // In case the new config does not contain this document type
            LOG(debug, "Reconfiguring documentdb with config with generation %" PRId64, dbConfig->getGeneration());
            owner->reconfigure(dbConfig);
        }
    }
}

void
ProtonConfigurer::fetchConfigs()
{
    LOG(debug, "Waiting for new config generation");
    bool configured = false;
    while (!configured) {
        ConfigSnapshot bootstrapSnapshot = _retriever.getBootstrapConfigs(5000);
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
                        updateDocumentDBConfigs(config, snapshot);
                        needsMoreConfig = false;

                        // Perform callbacks
                        reconfigureBootstrap(bootstrapSnapshot);
                        reconfigureDocumentDBs();
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
ProtonConfigurer::getGeneration() const
{
    return _retriever.getGeneration();
}

void
ProtonConfigurer::start()
{
    fetchConfigs();
    if (_threadPool.NewThread(this, NULL) == NULL) {
        throw vespalib::IllegalStateException(
                "Failed starting thread for proton configurer");
    }
}

void
ProtonConfigurer::close()
{
    if (!_retriever.isClosed()) {
        _retriever.close();
        _threadPool.Close();
    }
}

void
ProtonConfigurer::registerDocumentDB(const DocTypeName & docTypeName, IDocumentDBConfigOwner * owner)
{
    vespalib::LockGuard guard(_lock);
    assert(_documentDBOwnerMap.find(docTypeName) == _documentDBOwnerMap.end());
    LOG(debug, "Registering new document db with checker");
    _documentDBOwnerMap[docTypeName] = owner;
}

void
ProtonConfigurer::unregisterDocumentDB(const DocTypeName & docTypeName)
{
    vespalib::LockGuard guard(_lock);
    LOG(debug, "Removing document db from checker");
    assert(_documentDBOwnerMap.find(docTypeName) != _documentDBOwnerMap.end());
    _documentDBOwnerMap.erase(docTypeName);
}

DocumentDBConfig::SP
ProtonConfigurer::getDocumentDBConfig(const DocTypeName & docTypeName) const
{
    vespalib::LockGuard guard(_lock);
    DBManagerMap::const_iterator it(_dbManagerMap.find(docTypeName));
    if (it == _dbManagerMap.end())
        return DocumentDBConfig::SP();

    return it->second->getConfig();
}

} // namespace proton
