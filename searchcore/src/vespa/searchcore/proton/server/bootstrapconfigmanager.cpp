// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bootstrapconfigmanager.h"
#include "bootstrapconfig.h"
#include <vespa/document/repo/document_type_repo_factory.h>
#include <vespa/searchcore/proton/common/hw_info_sampler.h>
#include <vespa/config-bucketspaces.h>
#include <vespa/searchlib/common/tunefileinfo.hpp>
#include <vespa/vespalib/io/fileutil.h>


#include <vespa/log/log.h>
LOG_SETUP(".proton.server.bootstrapconfigmanager");

using namespace vespa::config::search;
using namespace config;
using document::DocumentTypeRepo;
using search::TuneFileDocumentDB;
using vespa::config::search::core::ProtonConfig;
using cloud::config::filedistribution::FiledistributorrpcConfig;
using vespa::config::content::core::BucketspacesConfig;
using document::DocumenttypesConfig;
using document::DocumentTypeRepoFactory;
using BucketspacesConfigSP = std::shared_ptr<BucketspacesConfig>;

namespace proton {

BootstrapConfigManager::BootstrapConfigManager(const vespalib::string & configId)
    : _pendingConfigSnapshot(),
      _configId(configId),
      _pendingConfigMutex()
{ }

BootstrapConfigManager::~BootstrapConfigManager() { }


const ConfigKeySet
BootstrapConfigManager::createConfigKeySet() const
{
    return ConfigKeySet().add<ProtonConfig>(_configId)
                         .add<DocumenttypesConfig>(_configId)
                         .add<FiledistributorrpcConfig>(_configId)
                         .add<BucketspacesConfig>(_configId);
}

std::shared_ptr<BootstrapConfig>
BootstrapConfigManager::getConfig() const
{
    std::lock_guard<std::mutex> lock(_pendingConfigMutex);
    return _pendingConfigSnapshot;
}

void
BootstrapConfigManager::update(const ConfigSnapshot & snapshot)
{
    typedef BootstrapConfig::ProtonConfigSP ProtonConfigSP;
    typedef BootstrapConfig::DocumenttypesConfigSP DocumenttypesConfigSP;

    ProtonConfigSP newProtonConfig;
    BootstrapConfig::FiledistributorrpcConfigSP newFiledistRpcConfSP;
    TuneFileDocumentDB::SP newTuneFileDocumentDB;
    DocumenttypesConfigSP newDocumenttypesConfig;
    std::shared_ptr<const DocumentTypeRepo> newRepo;
    BucketspacesConfigSP newBucketspacesConfig;
    int64_t currentGen = -1;

    BootstrapConfig::SP current = _pendingConfigSnapshot;
    if (current) {
        newProtonConfig = current->getProtonConfigSP();
        newFiledistRpcConfSP = current->getFiledistributorrpcConfigSP();
        newTuneFileDocumentDB = current->getTuneFileDocumentDBSP();
        newDocumenttypesConfig = current->getDocumenttypesConfigSP();
        newRepo = current->getDocumentTypeRepoSP();
        newBucketspacesConfig = current->getBucketspacesConfigSP();
        currentGen = current->getGeneration();
    }

    if (snapshot.isChanged<ProtonConfig>(_configId, currentGen)) {
        LOG(spam, "Proton config is changed");
        std::unique_ptr<ProtonConfig> protonConfig = snapshot.getConfig<ProtonConfig>(_configId);
        TuneFileDocumentDB::SP tuneFileDocumentDB(new TuneFileDocumentDB);
        TuneFileDocumentDB &tune = *tuneFileDocumentDB;
        ProtonConfig &conf = *protonConfig;
        tune._index._indexing._write.setFromConfig<ProtonConfig::Indexing::Write>(conf.indexing.write.io);
        tune._index._indexing._read.setFromConfig<ProtonConfig::Indexing::Read>(conf.indexing.read.io);
        tune._attr._write.setFromConfig<ProtonConfig::Attribute::Write>(conf.attribute.write.io);
        tune._index._search._read.setFromConfig<ProtonConfig::Search, ProtonConfig::Search::Mmap>(conf.search.io, conf.search.mmap);
        tune._summary._write.setFromConfig<ProtonConfig::Summary::Write>(conf.summary.write.io);
        tune._summary._seqRead.setFromConfig<ProtonConfig::Summary::Read>(conf.summary.read.io);
        tune._summary._randRead.setFromConfig<ProtonConfig::Summary::Read, ProtonConfig::Summary::Read::Mmap>(conf.summary.read.io, conf.summary.read.mmap);

        newProtonConfig = ProtonConfigSP(protonConfig.release());
        newTuneFileDocumentDB = tuneFileDocumentDB;
    }

    if (snapshot.isChanged<FiledistributorrpcConfig>(_configId, currentGen)) {
        LOG(info, "Filedistributorrpc config is changed");
        newFiledistRpcConfSP = snapshot.getConfig<FiledistributorrpcConfig>(_configId);
    }

    if (snapshot.isChanged<DocumenttypesConfig>(_configId, currentGen)) {
        LOG(spam, "Documenttypes config is changed");
        newDocumenttypesConfig = snapshot.getConfig<DocumenttypesConfig>(_configId);
        newRepo = DocumentTypeRepoFactory::make(*newDocumenttypesConfig);
    }
    if (snapshot.isChanged<BucketspacesConfig>(_configId, currentGen)) {
        LOG(spam, "Bucketspaces config is changed");
        newBucketspacesConfig = snapshot.getConfig<BucketspacesConfig>(_configId);
    }
    assert(newProtonConfig);
    assert(newFiledistRpcConfSP);
    assert(newBucketspacesConfig);
    assert(newTuneFileDocumentDB);
    assert(newDocumenttypesConfig);
    assert(newRepo);

    const ProtonConfig &protonConfig = *newProtonConfig;
    const auto &hwDiskCfg = protonConfig.hwinfo.disk;
    const auto &hwMemoryCfg = protonConfig.hwinfo.memory;
    const auto &hwCpuCfg = protonConfig.hwinfo.cpu;
    HwInfoSampler::Config samplerCfg(hwDiskCfg.size, hwDiskCfg.writespeed, hwDiskCfg.slowwritespeedlimit,
                                     hwDiskCfg.samplewritesize, hwDiskCfg.shared, hwMemoryCfg.size, hwCpuCfg.cores);
    vespalib::mkdir(protonConfig.basedir, true);
    HwInfoSampler sampler(protonConfig.basedir, samplerCfg);

    auto newSnapshot(std::make_shared<BootstrapConfig>(snapshot.getGeneration(), newDocumenttypesConfig, newRepo,
                                                       newProtonConfig, newFiledistRpcConfSP, newBucketspacesConfig,
                                                       newTuneFileDocumentDB, sampler.hwInfo()));

    assert(newSnapshot->valid());
    {
        std::lock_guard<std::mutex> lock(_pendingConfigMutex);
        _pendingConfigSnapshot = newSnapshot;
    }
}

} // namespace proton
