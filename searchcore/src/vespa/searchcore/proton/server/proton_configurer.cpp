// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "proton_configurer.h"
#include "proton_config_snapshot.h"
#include "bootstrapconfig.h"
#include "i_proton_configurer_owner.h"
#include "document_db_config_owner.h"
#include "document_db_directory_holder.h"
#include "i_proton_disk_layout.h"
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/threadstackexecutorbase.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/config-bucketspaces.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/retain_guard.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <future>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.proton_configurer");

using vespalib::makeLambdaTask;
using vespa::config::search::core::ProtonConfig;

namespace proton {

namespace {

document::BucketSpace
getBucketSpace(const BootstrapConfig &bootstrapConfig, const DocTypeName &name)
{
    const auto &bucketspaces = *bootstrapConfig.getBucketspacesConfigSP();
    for (const auto &entry : bucketspaces.documenttype) {
        if (entry.name == name.getName()) {
            return document::FixedBucketSpaces::from_string(entry.bucketspace);
        }
    }
    vespalib::asciistream ost;
    ost << "Could not map from document type name '" << name.getName() << "' to bucket space name";
    throw vespalib::IllegalStateException(ost.str(), VESPA_STRLOC);
}

}


ProtonConfigurer::ProtonConfigurer(vespalib::ThreadExecutor &executor,
                                   IProtonConfigurerOwner &owner,
                                   const std::unique_ptr<IProtonDiskLayout> &diskLayout)
    : IProtonConfigurer(),
      _executor(executor),
      _owner(owner),
      _documentDBs(),
      _pendingConfigSnapshot(),
      _activeConfigSnapshot(),
      _mutex(),
      _allowReconfig(false),
      _componentConfig(),
      _diskLayout(diskLayout)
{
}

class ProtonConfigurer::ReconfigureTask : public vespalib::Executor::Task {
public:
    explicit ReconfigureTask(ProtonConfigurer & configurer)
        : _configurer(configurer),
          _retainGuard(configurer._pendingReconfigureTasks)
    {}

    void run() override {
        _configurer.performReconfigure();
    }
private:
    ProtonConfigurer      & _configurer;
    vespalib::RetainGuard   _retainGuard;
};

ProtonConfigurer::~ProtonConfigurer() = default;

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
            _executor.execute(std::make_unique<ReconfigureTask>(*this));
        }
    }
    if (!allowReconfig) {
        // drain queued performReconfigure tasks
        _pendingReconfigureTasks.waitForZeroRefCount();
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
        _executor.execute(std::make_unique<ReconfigureTask>(*this));
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
    assert(!_activeConfigSnapshot == initialConfig);
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
    if (initialConfig) {
        pruneInitialDocumentDBDirs(*configSnapshot);
    }
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
    _activeConfigSnapshot = std::move(configSnapshot);
}

void
ProtonConfigurer::configureDocumentDB(const ProtonConfigSnapshot &configSnapshot,
                                      const DocTypeName &docTypeName,
                                      document::BucketSpace bucketSpace,
                                      const vespalib::string &configId,
                                      InitializeThreads initializeThreads)
{
    // called by proton executor thread
    const auto &bootstrapConfig = configSnapshot.getBootstrapConfig();
    const auto &documentDBConfigs = configSnapshot.getDocumentDBConfigs();
    auto cfgitr = documentDBConfigs.find(docTypeName);
    assert(cfgitr != documentDBConfigs.end());
    const auto &documentDBConfig = cfgitr->second;
    auto dbitr(_documentDBs.find(docTypeName));
    if (dbitr == _documentDBs.end()) {
        auto newdb = _owner.addDocumentDB(docTypeName, bucketSpace, configId, bootstrapConfig, documentDBConfig, std::move(initializeThreads));
        if (newdb) {
            auto insres = _documentDBs.insert(std::make_pair(docTypeName, std::make_pair(newdb, newdb->getDocumentDBDirectoryHolder())));
            assert(insres.second);
        }
    } else {
        auto documentDB = dbitr->second.first.lock();
        assert(documentDB);
        auto old_bucket_space = documentDB->getBucketSpace();
        if (bucketSpace != old_bucket_space) {
            vespalib::string old_bucket_space_name = document::FixedBucketSpaces::to_string(old_bucket_space);
            vespalib::string bucket_space_name = document::FixedBucketSpaces::to_string(bucketSpace);
            LOG(fatal, "Bucket space for document type %s changed from %s to %s. This triggers undefined behavior on a running system. Restarting process immediately to fix it.", docTypeName.getName().c_str(), old_bucket_space_name.c_str(), bucket_space_name.c_str());
            std::_Exit(1);
        }
        documentDB->reconfigure(documentDBConfig);
    }
}

void
ProtonConfigurer::pruneInitialDocumentDBDirs(const ProtonConfigSnapshot &configSnapshot)
{
    std::set<DocTypeName> docTypeNames;
    const auto &bootstrapConfig = configSnapshot.getBootstrapConfig();
    const ProtonConfig &protonConfig = bootstrapConfig->getProtonConfig();
    for (const auto &ddbConfig : protonConfig.documentdb) {
        docTypeNames.emplace(ddbConfig.inputdoctypename);
    }
    _diskLayout->initAndPruneUnused(docTypeNames);
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
            DocumentDBDirectoryHolder::waitUntilDestroyed(dbitr->second.second);
            _diskLayout->remove(dbitr->first);
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
    _executor.execute(makeLambdaTask([this, executor=std::move(initializeThreads), &promise]() mutable {
        applyConfig(getPendingConfigSnapshot(), std::move(executor), true);
        promise.set_value();
    }));
    future.wait();
}

} // namespace proton
