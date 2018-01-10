// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "proton_configurer.h"
#include "proton_config_snapshot.h"
#include "bootstrapconfig.h"
#include "i_proton_configurer_owner.h"
#include "i_document_db_config_owner.h"
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/threadstackexecutorbase.h>
#include <vespa/persistence/spi/fixed_bucket_spaces.h>
#include <vespa/config-bucketspaces.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <future>

using vespalib::makeLambdaTask;
using vespa::config::search::core::ProtonConfig;

namespace proton {

namespace {

document::BucketSpace
getBucketSpace(const BootstrapConfig &bootstrapConfig, const DocTypeName &name)
{
    const auto &bucketspaces = *bootstrapConfig.getBucketspacesConfigSP();
    if (bucketspaces.enableMultipleBucketSpaces) {
        for (const auto &entry : bucketspaces.documenttype) {
            if (entry.name == name.getName()) {
                return storage::spi::FixedBucketSpaces::from_string(entry.bucketspace);
            }
        }
        vespalib::asciistream ost;
        ost << "Could not map from document type name '" << name.getName() << "' to bucket space name";
        throw vespalib::IllegalStateException(ost.str(), VESPA_STRLOC);
    }
    return document::BucketSpace::placeHolder();
}

}


ProtonConfigurer::ProtonConfigurer(vespalib::ThreadStackExecutorBase &executor,
                                   IProtonConfigurerOwner &owner)
    : IProtonConfigurer(),
      _executor(executor),
      _owner(owner),
      _documentDBs(),
      _pendingConfigSnapshot(),
      _activeConfigSnapshot(),
      _mutex(),
      _allowReconfig(false),
      _componentConfig()
{
}

ProtonConfigurer::~ProtonConfigurer()
{
}

void
ProtonConfigurer::setAllowReconfig(bool allowReconfig)
{
    // called by proton app main thread
    assert(!_executor.isCurrentThread());
    {
        std::lock_guard<std::mutex> guard(_mutex);
        _allowReconfig = allowReconfig;
        if (allowReconfig) {
            // Ensure that pending config is applied
            _executor.execute(makeLambdaTask([=]() { performReconfigure(); }));
        }
    }
    if (!allowReconfig) {
        _executor.sync(); // drain queued performReconfigure tasks
    }
}

std::shared_ptr<ProtonConfigSnapshot>
ProtonConfigurer::getPendingConfigSnapshot()
{
    std::lock_guard<std::mutex> guard(_mutex);
    return _pendingConfigSnapshot;
}

std::shared_ptr<ProtonConfigSnapshot>
ProtonConfigurer::getActiveConfigSnapshot() const
{
    std::lock_guard<std::mutex> guard(_mutex);
    return _activeConfigSnapshot;
}

void
ProtonConfigurer::reconfigure(std::shared_ptr<ProtonConfigSnapshot> configSnapshot)
{
    // called by proton config fetcher thread
    assert(!_executor.isCurrentThread());
    std::lock_guard<std::mutex> guard(_mutex);
    _pendingConfigSnapshot = configSnapshot;
    if (_allowReconfig) {
        _executor.execute(makeLambdaTask([=]() { performReconfigure(); }));
    }
}

void
ProtonConfigurer::performReconfigure()
{
    // called by proton executor thread
    assert(_executor.isCurrentThread());
    auto configSnapshot(getPendingConfigSnapshot());
    applyConfig(configSnapshot, InitializeThreads(), false);
}

bool
ProtonConfigurer::skipConfig(const ProtonConfigSnapshot *configSnapshot, bool initialConfig)
{
    // called by proton executor thread
    std::lock_guard<std::mutex> guard(_mutex);
    assert((_activeConfigSnapshot.get() == nullptr) == initialConfig);
    if (_activeConfigSnapshot.get() == configSnapshot) {
        return true; // config snapshot already applied
    }
    if (!initialConfig && !_allowReconfig) {
        return true; // reconfig not allowed
    }
    return false;
}

void
ProtonConfigurer::applyConfig(std::shared_ptr<ProtonConfigSnapshot> configSnapshot,
                              InitializeThreads initializeThreads, bool initialConfig)
{
    // called by proton executor thread
    assert(_executor.isCurrentThread());
    if (skipConfig(configSnapshot.get(), initialConfig)) {
        return; // config should be skipped
    }
    const auto &bootstrapConfig = configSnapshot->getBootstrapConfig();
    const ProtonConfig &protonConfig = bootstrapConfig->getProtonConfig();
    _owner.applyConfig(bootstrapConfig);
    for (const auto &ddbConfig : protonConfig.documentdb) {
        DocTypeName docTypeName(ddbConfig.inputdoctypename);
        document::BucketSpace bucketSpace = getBucketSpace(*bootstrapConfig, docTypeName);
        configureDocumentDB(*configSnapshot, docTypeName, bucketSpace, ddbConfig.configid, initializeThreads);
    }
    pruneDocumentDBs(*configSnapshot);
    size_t gen = bootstrapConfig->getGeneration();
    _componentConfig.addConfig({"proton", gen});
    std::lock_guard<std::mutex> guard(_mutex);
    _activeConfigSnapshot = configSnapshot;
}

void
ProtonConfigurer::configureDocumentDB(const ProtonConfigSnapshot &configSnapshot,
                                      const DocTypeName &docTypeName,
                                      document::BucketSpace bucketSpace,
                                      const vespalib::string &configId,
                                      const InitializeThreads &initializeThreads)
{
    // called by proton executor thread
    const auto &bootstrapConfig = configSnapshot.getBootstrapConfig();
    const auto &documentDBConfigs = configSnapshot.getDocumentDBConfigs();
    auto cfgitr = documentDBConfigs.find(docTypeName);
    assert(cfgitr != documentDBConfigs.end());
    const auto &documentDBConfig = cfgitr->second;
    auto dbitr(_documentDBs.find(docTypeName));
    if (dbitr == _documentDBs.end()) {
        auto *newdb = _owner.addDocumentDB(docTypeName, bucketSpace, configId, bootstrapConfig, documentDBConfig, initializeThreads);
        if (newdb != nullptr) {
            auto insres = _documentDBs.insert(std::make_pair(docTypeName, newdb));
            assert(insres.second);
        }
    } else {
        dbitr->second->reconfigure(documentDBConfig);
    }
}

void
ProtonConfigurer::pruneDocumentDBs(const ProtonConfigSnapshot &configSnapshot)
{
    // called by proton executor thread
    const auto &bootstrapConfig = configSnapshot.getBootstrapConfig();
    const ProtonConfig &protonConfig = bootstrapConfig->getProtonConfig();
    using DocTypeSet = std::set<DocTypeName>;
    DocTypeSet newDocTypes;
    for (const auto &ddbConfig : protonConfig.documentdb) {
        DocTypeName docTypeName(ddbConfig.inputdoctypename);
        newDocTypes.insert(docTypeName);
    }
    auto dbitr = _documentDBs.begin();
    while (dbitr != _documentDBs.end()) {
        auto found(newDocTypes.find(dbitr->first));
        if (found == newDocTypes.end()) {
            _owner.removeDocumentDB(dbitr->first);
            dbitr = _documentDBs.erase(dbitr);
        } else {
            ++dbitr;
        }
    }
}

void
ProtonConfigurer::applyInitialConfig(InitializeThreads initializeThreads)
{
    // called by proton app main thread
    assert(!_executor.isCurrentThread());
    std::promise<void> promise;
    auto future = promise.get_future();
    _executor.execute(makeLambdaTask([this, initializeThreads, &promise]() { applyConfig(getPendingConfigSnapshot(), initializeThreads, true); promise.set_value(); }));
    future.wait();
}

} // namespace proton
