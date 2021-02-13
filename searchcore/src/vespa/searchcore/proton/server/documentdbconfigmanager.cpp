// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentdbconfigmanager.h"
#include "bootstrapconfig.h"
#include "threading_service_config.h"
#include <vespa/searchcore/proton/common/alloc_config.h>
#include <vespa/searchcore/proton/common/hw_info.h>
#include <vespa/searchcore/config/config-ranking-constants.h>
#include <vespa/searchcore/config/config-onnx-models.h>
#include <vespa/config-imported-fields.h>
#include <vespa/config-rank-profiles.h>
#include <vespa/config-summarymap.h>
#include <vespa/config/file_acquirer/file_acquirer.h>
#include <vespa/config/common/configcontext.h>
#include <vespa/config/helper/legacy.h>
#include <vespa/config-attributes.h>
#include <vespa/config-indexschema.h>
#include <vespa/config-summary.h>
#include <vespa/searchcommon/common/schemaconfigurer.h>
#include <vespa/searchlib/index/schemautil.h>
#include <vespa/searchsummary/config/config-juniperrc.h>
#include <vespa/vespalib/time/time_box.h>
#include <thread>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.documentdbconfigmanager");

using namespace config;
using namespace vespa::config::search::core;
using namespace vespa::config::search::summary;
using namespace vespa::config::search;

using document::DocumentTypeRepo;
using search::TuneFileDocumentDB;
using search::index::Schema;
using search::index::SchemaBuilder;
using proton::matching::RankingConstants;
using proton::matching::OnnxModels;
using vespalib::compression::CompressionConfig;
using search::LogDocumentStore;
using search::LogDataStore;
using search::DocumentStore;
using search::WriteableFileChunk;
using std::make_shared;
using std::make_unique;

namespace proton {

const ConfigKeySet
DocumentDBConfigManager::createConfigKeySet() const
{
    ConfigKeySet set;
    set.add<RankProfilesConfig,
            RankingConstantsConfig,
            OnnxModelsConfig,
            IndexschemaConfig,
            AttributesConfig,
            SummaryConfig,
            SummarymapConfig,
            JuniperrcConfig,
            ImportedFieldsConfig>(_configId);
    return set;
}

namespace {

Schema::SP
buildNewSchema(const AttributesConfig &newAttributesConfig,
               const SummaryConfig &newSummaryConfig,
               const IndexschemaConfig &newIndexschemaConfig)
{
    Schema::SP schema = std::make_shared<Schema>();
    SchemaBuilder::build(newAttributesConfig, *schema);
    SchemaBuilder::build(newSummaryConfig, *schema);
    SchemaBuilder::build(newIndexschemaConfig, *schema);
    return schema;
}

}

Schema::SP
DocumentDBConfigManager::buildSchema(const AttributesConfig &newAttributesConfig,
                                     const SummaryConfig &newSummaryConfig,
                                     const IndexschemaConfig &newIndexschemaConfig)
{
    // Called with lock held
    Schema::SP oldSchema;
    if (_pendingConfigSnapshot) {
        oldSchema = _pendingConfigSnapshot->getSchemaSP();
    }
    if (!oldSchema) {
        return buildNewSchema(newAttributesConfig, newSummaryConfig, newIndexschemaConfig);
    }
    const DocumentDBConfig &old = *_pendingConfigSnapshot;
    if (old.getAttributesConfig() != newAttributesConfig ||
        old.getSummaryConfig() != newSummaryConfig ||
        old.getIndexschemaConfig() != newIndexschemaConfig)
    {
        Schema::SP schema(buildNewSchema(newAttributesConfig, newSummaryConfig, newIndexschemaConfig));
        return (*oldSchema == *schema) ? oldSchema : schema;
    }
    return oldSchema;
}

namespace {

DocumentDBMaintenanceConfig::SP
buildMaintenanceConfig(const BootstrapConfig::SP &bootstrapConfig,
                       const vespalib::string &docTypeName)
{
    typedef ProtonConfig::Documentdb DdbConfig;
    ProtonConfig &proton(bootstrapConfig->getProtonConfig());

    vespalib::duration visibilityDelay = vespalib::duration::zero();
    bool isDocumentTypeGlobal = false;
    // Use document type to find document db config in proton config
    uint32_t index;
    for (index = 0; index < proton.documentdb.size(); ++index) {
        const DdbConfig &ddbConfig = proton.documentdb[index];
        if (docTypeName == ddbConfig.inputdoctypename)
            break;
    }
    vespalib::duration pruneRemovedDocumentsAge = vespalib::from_s(proton.pruneremoveddocumentsage);
    vespalib::duration pruneRemovedDocumentsInterval = (proton.pruneremoveddocumentsinterval == 0)
            ? (pruneRemovedDocumentsAge / 100)
            : vespalib::from_s(proton.pruneremoveddocumentsinterval);

    if (index < proton.documentdb.size()) {
        const DdbConfig &ddbConfig = proton.documentdb[index];
        visibilityDelay = vespalib::from_s(std::min(proton.maxvisibilitydelay, ddbConfig.visibilitydelay));
        isDocumentTypeGlobal = ddbConfig.global;
    }
    return std::make_shared<DocumentDBMaintenanceConfig>(
            DocumentDBPruneRemovedDocumentsConfig(
                    pruneRemovedDocumentsInterval,
                    pruneRemovedDocumentsAge),
            DocumentDBHeartBeatConfig(),
            vespalib::from_s(proton.grouping.sessionmanager.pruning.interval),
            visibilityDelay,
            DocumentDBLidSpaceCompactionConfig(
                    vespalib::from_s(proton.lidspacecompaction.interval),
                    proton.lidspacecompaction.allowedlidbloat,
                    proton.lidspacecompaction.allowedlidbloatfactor,
                    proton.lidspacecompaction.removebatchblockrate,
                    proton.lidspacecompaction.removeblockrate,
                    isDocumentTypeGlobal,
                    proton.lidspacecompaction.usebucketexecutor),
            AttributeUsageFilterConfig(
                    proton.writefilter.attribute.enumstorelimit,
                    proton.writefilter.attribute.multivaluelimit),
            vespalib::from_s(proton.writefilter.sampleinterval),
            BlockableMaintenanceJobConfig(
                    proton.maintenancejobs.resourcelimitfactor,
                    proton.maintenancejobs.maxoutstandingmoveops),
            DocumentDBFlushConfig(proton.index.maxflushed,proton.index.maxflushedretired),
            BucketMoveConfig(proton.bucketmove.maxdocstomoveperbucket, proton.bucketmove.usebucketexecutor));
}

template<typename T>
CompressionConfig
deriveCompression(const T & config) {
    CompressionConfig compression;
    if (config.type == T::Type::LZ4) {
        compression.type = CompressionConfig::LZ4;
    } else if (config.type == T::Type::ZSTD) {
        compression.type = CompressionConfig::ZSTD;
    }
    compression.compressionLevel = config.level;
    return compression;
}

DocumentStore::Config::UpdateStrategy
derive(ProtonConfig::Summary::Cache::UpdateStrategy strategy) {
    switch (strategy) {
        case ProtonConfig::Summary::Cache::UpdateStrategy::INVALIDATE:
            return DocumentStore::Config::UpdateStrategy::INVALIDATE;
        case ProtonConfig::Summary::Cache::UpdateStrategy::UPDATE:
            return DocumentStore::Config::UpdateStrategy::UPDATE;
    }
    return DocumentStore::Config::UpdateStrategy::INVALIDATE;
}

DocumentStore::Config
getStoreConfig(const ProtonConfig::Summary::Cache & cache, const HwInfo & hwInfo)
{
    size_t maxBytes = (cache.maxbytes < 0)
                      ? (hwInfo.memory().sizeBytes()*std::min(INT64_C(50), -cache.maxbytes))/100l
                      : cache.maxbytes;
    return DocumentStore::Config(deriveCompression(cache.compression), maxBytes, cache.initialentries)
            .allowVisitCaching(cache.allowvisitcaching)
            .updateStrategy(derive(cache.updateStrategy));
}

LogDocumentStore::Config
deriveConfig(const ProtonConfig::Summary & summary, const ProtonConfig::Flush::Memory & flush, const HwInfo & hwInfo) {
    DocumentStore::Config config(getStoreConfig(summary.cache, hwInfo));
    const ProtonConfig::Summary::Log & log(summary.log);
    const ProtonConfig::Summary::Log::Chunk & chunk(log.chunk);
    WriteableFileChunk::Config fileConfig(deriveCompression(chunk.compression), chunk.maxbytes);
    LogDataStore::Config logConfig;
    logConfig.setMaxFileSize(log.maxfilesize)
            .setMaxNumLids(log.maxnumlids)
            .setMaxDiskBloatFactor(std::min(flush.diskbloatfactor, flush.each.diskbloatfactor))
            .setMaxBucketSpread(log.maxbucketspread).setMinFileSizeFactor(log.minfilesizefactor)
            .compactCompression(deriveCompression(log.compact.compression))
            .setFileConfig(fileConfig).disableCrcOnRead(chunk.skipcrconread);
    return LogDocumentStore::Config(config, logConfig);
}

search::LogDocumentStore::Config buildStoreConfig(const ProtonConfig & proton, const HwInfo & hwInfo) {
    return deriveConfig(proton.summary, proton.flush.memory, hwInfo);
}

using AttributesConfigSP = DocumentDBConfig::AttributesConfigSP;
using AttributesConfigBuilder = vespa::config::search::AttributesConfigBuilder;
using AttributesConfigBuilderSP = std::shared_ptr<AttributesConfigBuilder>;

AttributesConfigSP
filterImportedAttributes(const AttributesConfigSP &attrCfg)
{
    AttributesConfigBuilderSP result = std::make_shared<AttributesConfigBuilder>();
    result->attribute.reserve(attrCfg->attribute.size());
    for (const auto &attr : attrCfg->attribute) {
        if (!attr.imported) {
            result->attribute.push_back(attr);
        }
    }
    return result;
}

const ProtonConfig::Documentdb default_document_db_config_entry;

const ProtonConfig::Documentdb&
find_document_db_config_entry(const ProtonConfig::DocumentdbVector& document_dbs, const vespalib::string& doc_type_name) {
    for (const auto & db_cfg : document_dbs) {
        if (db_cfg.inputdoctypename == doc_type_name) {
            return db_cfg;
        }
    }
    return default_document_db_config_entry;
}

std::shared_ptr<const ThreadingServiceConfig>
build_threading_service_config(const ProtonConfig &proton_config,
                               const HwInfo &hw_info,
                               const vespalib::string& doc_type_name)
{
    auto& document_db_config_entry = find_document_db_config_entry(proton_config.documentdb, doc_type_name);
    return std::make_shared<const ThreadingServiceConfig>
        (ThreadingServiceConfig::make(proton_config,
                                      document_db_config_entry.feeding.concurrency,
                                      hw_info.cpu()));
}

std::shared_ptr<const AllocConfig>
build_alloc_config(const ProtonConfig& proton_config, const vespalib::string& doc_type_name)
{
    auto& document_db_config_entry = find_document_db_config_entry(proton_config.documentdb, doc_type_name);
    auto& alloc_config = document_db_config_entry.allocation;
    auto& distribution_config = proton_config.distribution;
    search::GrowStrategy grow_strategy(alloc_config.initialnumdocs, alloc_config.growfactor, alloc_config.growbias, alloc_config.multivaluegrowfactor);
    search::CompactionStrategy compaction_strategy(alloc_config.maxDeadBytesRatio, alloc_config.maxDeadAddressSpaceRatio);
    return std::make_shared<const AllocConfig>
        (AllocStrategy(grow_strategy, compaction_strategy, alloc_config.amortizecount),
         distribution_config.redundancy, distribution_config.searchablecopies);
}

}

void
DocumentDBConfigManager::update(const ConfigSnapshot &snapshot)
{
    using RankProfilesConfigSP = DocumentDBConfig::RankProfilesConfigSP;
    using RankingConstantsConfigSP = std::shared_ptr<vespa::config::search::core::RankingConstantsConfig>;
    using OnnxModelsConfigSP = std::shared_ptr<vespa::config::search::core::OnnxModelsConfig>;
    using IndexschemaConfigSP = DocumentDBConfig::IndexschemaConfigSP;
    using SummaryConfigSP = DocumentDBConfig::SummaryConfigSP;
    using SummarymapConfigSP = DocumentDBConfig::SummarymapConfigSP;
    using JuniperrcConfigSP = DocumentDBConfig::JuniperrcConfigSP;
    using ImportedFieldsConfigSP = DocumentDBConfig::ImportedFieldsConfigSP;
    using MaintenanceConfigSP = DocumentDBConfig::MaintenanceConfigSP;

    DocumentDBConfig::SP current = _pendingConfigSnapshot;
    RankProfilesConfigSP newRankProfilesConfig;
    matching::RankingConstants::SP newRankingConstants;
    matching::OnnxModels::SP newOnnxModels;
    IndexschemaConfigSP newIndexschemaConfig;
    MaintenanceConfigSP oldMaintenanceConfig;
    MaintenanceConfigSP newMaintenanceConfig;
    std::shared_ptr<const ThreadingServiceConfig> old_threading_service_config;
    std::shared_ptr<const AllocConfig> old_alloc_config;

    if (!_ignoreForwardedConfig) {
        if (!(_bootstrapConfig->getDocumenttypesConfigSP() &&
            _bootstrapConfig->getDocumentTypeRepoSP() &&
            _bootstrapConfig->getProtonConfigSP() &&
            _bootstrapConfig->getTuneFileDocumentDBSP())) {
            return;
        }
    }

    int64_t generation = snapshot.getGeneration();
    LOG(debug, "Forwarded generation %" PRId64 ", generation %" PRId64, _bootstrapConfig->getGeneration(), generation);
    if (!_ignoreForwardedConfig && _bootstrapConfig->getGeneration() != generation) {
        return;
    }

    int64_t currentGeneration = -1;
    if (current) {
        newRankProfilesConfig = current->getRankProfilesConfigSP();
        newRankingConstants = current->getRankingConstantsSP();
        newOnnxModels = current->getOnnxModelsSP();
        newIndexschemaConfig = current->getIndexschemaConfigSP();
        oldMaintenanceConfig = current->getMaintenanceConfigSP();
        old_threading_service_config = current->get_threading_service_config_shared_ptr();
        old_alloc_config = current->get_alloc_config_shared_ptr();
        currentGeneration = current->getGeneration();
    }

    if (snapshot.isChanged<RankProfilesConfig>(_configId, currentGeneration)) {
        newRankProfilesConfig = snapshot.getConfig<RankProfilesConfig>(_configId);
    }
    if (snapshot.isChanged<RankingConstantsConfig>(_configId, currentGeneration)) {
        RankingConstantsConfigSP newRankingConstantsConfig = RankingConstantsConfigSP(
                snapshot.getConfig<RankingConstantsConfig>(_configId));
        const vespalib::string &spec = _bootstrapConfig->getFiledistributorrpcConfig().connectionspec;
        RankingConstants::Vector constants;
        if (spec != "") {
            config::RpcFileAcquirer fileAcquirer(spec);
            vespalib::TimeBox timeBox(5*60, 5);
            for (const RankingConstantsConfig::Constant &rc : newRankingConstantsConfig->constant) {
                vespalib::string filePath;
                LOG(info, "Waiting for file acquirer (name='%s', type='%s', ref='%s')",
                    rc.name.c_str(), rc.type.c_str(), rc.fileref.c_str());
                while (timeBox.hasTimeLeft() && (filePath == "")) {
                    filePath = fileAcquirer.wait_for(rc.fileref, timeBox.timeLeft());
                    if (filePath == "") {
                        std::this_thread::sleep_for(std::chrono::milliseconds(100));
                    }
                }
                LOG(info, "Got file path from file acquirer: '%s' (name='%s', type='%s', ref='%s')",
                    filePath.c_str(), rc.name.c_str(), rc.type.c_str(), rc.fileref.c_str());
                constants.emplace_back(rc.name, rc.type, filePath);
            }
        }
        newRankingConstants = std::make_shared<RankingConstants>(constants);
    }
    if (snapshot.isChanged<OnnxModelsConfig>(_configId, currentGeneration)) {
        OnnxModelsConfigSP newOnnxModelsConfig = OnnxModelsConfigSP(
                snapshot.getConfig<OnnxModelsConfig>(_configId));
        const vespalib::string &spec = _bootstrapConfig->getFiledistributorrpcConfig().connectionspec;
        OnnxModels::Vector models;
        if (spec != "") {
            config::RpcFileAcquirer fileAcquirer(spec);
            vespalib::TimeBox timeBox(5*60, 5);
            for (const OnnxModelsConfig::Model &rc : newOnnxModelsConfig->model) {
                vespalib::string filePath;
                LOG(info, "Waiting for file acquirer (name='%s', ref='%s')",
                    rc.name.c_str(), rc.fileref.c_str());
                while (timeBox.hasTimeLeft() && (filePath == "")) {
                    filePath = fileAcquirer.wait_for(rc.fileref, timeBox.timeLeft());
                    if (filePath == "") {
                        std::this_thread::sleep_for(std::chrono::milliseconds(100));
                    }
                }
                LOG(info, "Got file path from file acquirer: '%s' (name='%s', ref='%s')",
                    filePath.c_str(), rc.name.c_str(), rc.fileref.c_str());
                models.emplace_back(rc.name, filePath);
                OnnxModels::configure(rc, models.back());
            }
        }
        newOnnxModels = std::make_shared<OnnxModels>(models);
    }
    if (snapshot.isChanged<IndexschemaConfig>(_configId, currentGeneration)) {
        newIndexschemaConfig = snapshot.getConfig<IndexschemaConfig>(_configId);
        search::index::Schema schema;
        search::index::SchemaBuilder::build(*newIndexschemaConfig, schema);
        if (!search::index::SchemaUtil::validateSchema(schema)) {
            LOG_ABORT("Cannot use bad index schema, validation failed");
        }
    }
    AttributesConfigSP newAttributesConfig = snapshot.getConfig<AttributesConfig>(_configId);
    SummaryConfigSP newSummaryConfig = snapshot.getConfig<SummaryConfig>(_configId);
    SummarymapConfigSP newSummarymapConfig = snapshot.getConfig<SummarymapConfig>(_configId);
    JuniperrcConfigSP newJuniperrcConfig = snapshot.getConfig<JuniperrcConfig>(_configId);
    ImportedFieldsConfigSP newImportedFieldsConfig = snapshot.getConfig<ImportedFieldsConfig>(_configId);

    Schema::SP schema(buildSchema(*newAttributesConfig, *newSummaryConfig, *newIndexschemaConfig));
    newMaintenanceConfig = buildMaintenanceConfig(_bootstrapConfig, _docTypeName);
    search::LogDocumentStore::Config storeConfig = buildStoreConfig(_bootstrapConfig->getProtonConfig(),
                                                                    _bootstrapConfig->getHwInfo());
    if (newMaintenanceConfig && oldMaintenanceConfig && (*newMaintenanceConfig == *oldMaintenanceConfig)) {
        newMaintenanceConfig = oldMaintenanceConfig;
    }
    auto new_threading_service_config = build_threading_service_config(_bootstrapConfig->getProtonConfig(), _bootstrapConfig->getHwInfo(), _docTypeName);
    if (new_threading_service_config && old_threading_service_config &&
        (*new_threading_service_config == *old_threading_service_config)) {
        new_threading_service_config = old_threading_service_config;
    }
    auto new_alloc_config = build_alloc_config(_bootstrapConfig->getProtonConfig(), _docTypeName);
    if (new_alloc_config && old_alloc_config &&(*new_alloc_config == *old_alloc_config)) {
        new_alloc_config = old_alloc_config;
    }
    auto newSnapshot = std::make_shared<DocumentDBConfig>(generation,
                                 newRankProfilesConfig,
                                 newRankingConstants,
                                 newOnnxModels,
                                 newIndexschemaConfig,
                                 filterImportedAttributes(newAttributesConfig),
                                 newSummaryConfig,
                                 newSummarymapConfig,
                                 newJuniperrcConfig,
                                 _bootstrapConfig->getDocumenttypesConfigSP(),
                                 _bootstrapConfig->getDocumentTypeRepoSP(),
                                 newImportedFieldsConfig,
                                 _bootstrapConfig->getTuneFileDocumentDBSP(),
                                 schema,
                                 newMaintenanceConfig,
                                 storeConfig,
                                 new_threading_service_config,
                                 new_alloc_config,
                                 _configId,
                                 _docTypeName);
    assert(newSnapshot->valid());
    {
        std::lock_guard<std::mutex> lock(_pendingConfigMutex);
        _pendingConfigSnapshot = newSnapshot;
    }
}


DocumentDBConfigManager::
DocumentDBConfigManager(const vespalib::string &configId, const vespalib::string &docTypeName)
    : _configId(configId),
      _docTypeName(docTypeName),
      _bootstrapConfig(),
      _pendingConfigSnapshot(),
      _ignoreForwardedConfig(true),
      _pendingConfigMutex()
{ }

DocumentDBConfigManager::~DocumentDBConfigManager() = default;

DocumentDBConfig::SP
DocumentDBConfigManager::getConfig() const {
    std::lock_guard<std::mutex> lock(_pendingConfigMutex);
    return _pendingConfigSnapshot;
}

void
DocumentDBConfigManager::
forwardConfig(const BootstrapConfig::SP & config)
{
    {
        if (!_ignoreForwardedConfig &&
            config->getGeneration() < _bootstrapConfig->getGeneration())
            return; // Enforce time direction
        _bootstrapConfig = config;
        _ignoreForwardedConfig = false;
    }
}

DocumentDBConfigHelper::DocumentDBConfigHelper(const DirSpec &spec, const vespalib::string &docTypeName)
    : _mgr("", docTypeName),
      _retriever(make_unique<ConfigRetriever>(_mgr.createConfigKeySet(), make_shared<ConfigContext>(spec)))
{ }

DocumentDBConfigHelper::~DocumentDBConfigHelper() = default;

bool
DocumentDBConfigHelper::nextGeneration(std::chrono::milliseconds timeoutInMillis)
{
    ConfigSnapshot snapshot(_retriever->getBootstrapConfigs(timeoutInMillis));
    if (snapshot.empty())
        return false;
    _mgr.update(snapshot);
    return true;
}

DocumentDBConfig::SP
DocumentDBConfigHelper::getConfig() const
{
    return _mgr.getConfig();
}

void
DocumentDBConfigHelper::forwardConfig(const std::shared_ptr<BootstrapConfig> & config)
{
    _mgr.forwardConfig(config);
}

} // namespace proton
