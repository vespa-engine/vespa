// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentdbconfigmanager.h"
#include "bootstrapconfig.h"
#include "threading_service_config.h"
#include <vespa/config-attributes.h>
#include <vespa/config-imported-fields.h>
#include <vespa/config-indexschema.h>
#include <vespa/config-onnx-models.h>
#include <vespa/config-rank-profiles.h>
#include <vespa/config-ranking-constants.h>
#include <vespa/config-ranking-expressions.h>
#include <vespa/config-summary.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/config/common/configcontext.h>
#include <vespa/config/retriever/configretriever.h>
#include <vespa/config/helper/legacy.h>
#include <vespa/searchcommon/common/schemaconfigurer.h>
#include <vespa/searchcore/proton/common/alloc_config.h>
#include <vespa/searchcore/proton/common/hw_info.h>
#include <vespa/searchlib/fef/ranking_assets_builder.h>
#include <vespa/searchlib/index/schemautil.h>
#include <vespa/searchsummary/config/config-juniperrc.h>
#include <vespa/config/retriever/configsnapshot.hpp>
#include <thread>
#include <cassert>
#include <cinttypes>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.documentdbconfigmanager");

using namespace config;
using namespace vespa::config::search::core;
using namespace vespa::config::search::summary;
using namespace vespa::config::search;

using document::DocumentTypeRepo;
using search::TuneFileDocumentDB;
using search::fef::OnnxModels;
using search::fef::RankingAssetsBuilder;
using search::fef::RankingConstants;
using search::fef::RankingExpressions;
using search::index::Schema;
using search::index::SchemaBuilder;
using vespalib::compression::CompressionConfig;
using search::LogDocumentStore;
using search::LogDataStore;
using search::DocumentStore;
using search::WriteableFileChunk;
using std::make_shared;
using std::make_unique;
using vespalib::datastore::CompactionStrategy;

namespace proton {

const ConfigKeySet
DocumentDBConfigManager::createConfigKeySet() const
{
    ConfigKeySet set;
    set.add<RankProfilesConfig,
            RankingConstantsConfig,
            RankingExpressionsConfig,
            OnnxModelsConfig,
            IndexschemaConfig,
            AttributesConfig,
            SummaryConfig,
            JuniperrcConfig,
            ImportedFieldsConfig>(_configId);
    return set;
}

Schema::SP
DocumentDBConfigManager::buildSchema(const AttributesConfig &newAttributesConfig,
                                     const IndexschemaConfig &newIndexschemaConfig)
{
    // Called with lock held
    Schema::SP oldSchema;
    if (_pendingConfigSnapshot) {
        oldSchema = _pendingConfigSnapshot->getSchemaSP();
    }
    if (!oldSchema) {
        return DocumentDBConfig::build_schema(newAttributesConfig, newIndexschemaConfig);
    }
    const DocumentDBConfig &old = *_pendingConfigSnapshot;
    if (old.getAttributesConfig() != newAttributesConfig ||
        old.getIndexschemaConfig() != newIndexschemaConfig)
    {
        auto schema = DocumentDBConfig::build_schema(newAttributesConfig, newIndexschemaConfig);
        return (*oldSchema == *schema) ? oldSchema : schema;
    }
    return oldSchema;
}

namespace {

DocumentDBMaintenanceConfig::SP
buildMaintenanceConfig(const BootstrapConfig::SP &bootstrapConfig,
                       const vespalib::string &docTypeName)
{
    using DdbConfig = ProtonConfig::Documentdb;
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
            DocumentDBPruneConfig(pruneRemovedDocumentsInterval,
                                  pruneRemovedDocumentsAge),
            DocumentDBHeartBeatConfig(),
            visibilityDelay,
            DocumentDBLidSpaceCompactionConfig(
                    vespalib::from_s(proton.lidspacecompaction.interval),
                    proton.lidspacecompaction.allowedlidbloat,
                    proton.lidspacecompaction.allowedlidbloatfactor,
                    proton.lidspacecompaction.removebatchblockrate,
                    proton.lidspacecompaction.removeblockrate,
                    isDocumentTypeGlobal),
            AttributeUsageFilterConfig(
                    proton.writefilter.attribute.addressSpaceLimit),
            vespalib::from_s(proton.writefilter.sampleinterval),
            BlockableMaintenanceJobConfig(
                    proton.maintenancejobs.resourcelimitfactor,
                    proton.maintenancejobs.maxoutstandingmoveops),
            DocumentDBFlushConfig(proton.index.maxflushed,proton.index.maxflushedretired),
            BucketMoveConfig(proton.bucketmove.maxdocstomoveperbucket));
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
    return DocumentStore::Config(deriveCompression(cache.compression), maxBytes)
            .updateStrategy(derive(cache.updateStrategy));
}

LogDocumentStore::Config
deriveConfig(const ProtonConfig::Summary & summary, const HwInfo & hwInfo) {
    DocumentStore::Config config(getStoreConfig(summary.cache, hwInfo));
    const ProtonConfig::Summary::Log & log(summary.log);
    const ProtonConfig::Summary::Log::Chunk & chunk(log.chunk);
    WriteableFileChunk::Config fileConfig(deriveCompression(chunk.compression), chunk.maxbytes);
    LogDataStore::Config logConfig;
    logConfig.setMaxFileSize(log.maxfilesize)
            .setMaxNumLids(log.maxnumlids)
            .setMaxBucketSpread(log.maxbucketspread).setMinFileSizeFactor(log.minfilesizefactor)
            .compactCompression(deriveCompression(log.compact.compression))
            .setFileConfig(fileConfig);
    return {config, logConfig};
}

search::LogDocumentStore::Config buildStoreConfig(const ProtonConfig & proton, const HwInfo & hwInfo) {
    return deriveConfig(proton.summary, hwInfo);
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

const AllocConfig
build_alloc_config(const ProtonConfig& proton_config, const vespalib::string& doc_type_name)
{
    auto& document_db_config_entry = find_document_db_config_entry(proton_config.documentdb, doc_type_name);
    auto& alloc_config = document_db_config_entry.allocation;
    auto& distribution_config = proton_config.distribution;
    search::GrowStrategy grow_strategy(alloc_config.initialnumdocs, alloc_config.growfactor, alloc_config.growbias, alloc_config.initialnumdocs, alloc_config.multivaluegrowfactor);
    CompactionStrategy compaction_strategy(alloc_config.maxDeadBytesRatio, alloc_config.maxDeadAddressSpaceRatio, alloc_config.maxCompactBuffers, alloc_config.activeBuffersRatio);
    return AllocConfig(AllocStrategy(grow_strategy, compaction_strategy, alloc_config.amortizecount),
                       distribution_config.redundancy, distribution_config.searchablecopies);
}

}

void
DocumentDBConfigManager::update(FNET_Transport & transport, const ConfigSnapshot &snapshot)
{
    using RankProfilesConfigSP = DocumentDBConfig::RankProfilesConfigSP;
    using RankingConstantsConfigSP = std::shared_ptr<vespa::config::search::core::RankingConstantsConfig>;
    using RankingExpressionsConfigSP = std::shared_ptr<vespa::config::search::core::RankingExpressionsConfig>;
    using OnnxModelsConfigSP = std::shared_ptr<vespa::config::search::core::OnnxModelsConfig>;
    using IndexschemaConfigSP = DocumentDBConfig::IndexschemaConfigSP;
    using SummaryConfigSP = DocumentDBConfig::SummaryConfigSP;
    using JuniperrcConfigSP = DocumentDBConfig::JuniperrcConfigSP;
    using ImportedFieldsConfigSP = DocumentDBConfig::ImportedFieldsConfigSP;
    using MaintenanceConfigSP = DocumentDBConfig::MaintenanceConfigSP;

    DocumentDBConfig::SP current = _pendingConfigSnapshot;
    RankProfilesConfigSP newRankProfilesConfig;
    std::shared_ptr<const RankingConstants> newRankingConstants;
    std::shared_ptr<const RankingExpressions> newRankingExpressions;
    std::shared_ptr<const OnnxModels> newOnnxModels;
    IndexschemaConfigSP newIndexschemaConfig;
    MaintenanceConfigSP oldMaintenanceConfig;
    MaintenanceConfigSP newMaintenanceConfig;

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
        newRankingExpressions = current->getRankingExpressionsSP();
        newOnnxModels = current->getOnnxModelsSP();
        newIndexschemaConfig = current->getIndexschemaConfigSP();
        oldMaintenanceConfig = current->getMaintenanceConfigSP();
        currentGeneration = current->getGeneration();
    }

    if (snapshot.isChanged<RankProfilesConfig>(_configId, currentGeneration)) {
        newRankProfilesConfig = snapshot.getConfig<RankProfilesConfig>(_configId);
    }
    RankingAssetsBuilder ranking_assets_builder(&transport, _bootstrapConfig->getFiledistributorrpcConfig().connectionspec);
    if (snapshot.isChanged<RankingConstantsConfig>(_configId, currentGeneration)) {
        RankingConstantsConfigSP newRankingConstantsConfig = RankingConstantsConfigSP(
                snapshot.getConfig<RankingConstantsConfig>(_configId));
        newRankingConstants = ranking_assets_builder.build(*newRankingConstantsConfig);
    }
    if (snapshot.isChanged<RankingExpressionsConfig>(_configId, currentGeneration)) {
        RankingExpressionsConfigSP newRankingExpressionsConfig = RankingExpressionsConfigSP(
            snapshot.getConfig<RankingExpressionsConfig>(_configId));
        newRankingExpressions = ranking_assets_builder.build(*newRankingExpressionsConfig);
    }
    if (snapshot.isChanged<OnnxModelsConfig>(_configId, currentGeneration)) {
        OnnxModelsConfigSP newOnnxModelsConfig = OnnxModelsConfigSP(
                snapshot.getConfig<OnnxModelsConfig>(_configId));
        newOnnxModels = ranking_assets_builder.build(*newOnnxModelsConfig);
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
    JuniperrcConfigSP newJuniperrcConfig = snapshot.getConfig<JuniperrcConfig>(_configId);
    ImportedFieldsConfigSP newImportedFieldsConfig = snapshot.getConfig<ImportedFieldsConfig>(_configId);

    Schema::SP schema(buildSchema(*newAttributesConfig, *newIndexschemaConfig));
    newMaintenanceConfig = buildMaintenanceConfig(_bootstrapConfig, _docTypeName);
    search::LogDocumentStore::Config storeConfig = buildStoreConfig(_bootstrapConfig->getProtonConfig(),
                                                                    _bootstrapConfig->getHwInfo());
    if (newMaintenanceConfig && oldMaintenanceConfig && (*newMaintenanceConfig == *oldMaintenanceConfig)) {
        newMaintenanceConfig = oldMaintenanceConfig;
    }
    auto newSnapshot = std::make_shared<DocumentDBConfig>(generation,
                                 newRankProfilesConfig,
                                 newRankingConstants,
                                 newRankingExpressions,
                                 newOnnxModels,
                                 newIndexschemaConfig,
                                 filterImportedAttributes(newAttributesConfig),
                                 newSummaryConfig,
                                 newJuniperrcConfig,
                                 _bootstrapConfig->getDocumenttypesConfigSP(),
                                 _bootstrapConfig->getDocumentTypeRepoSP(),
                                 newImportedFieldsConfig,
                                 _bootstrapConfig->getTuneFileDocumentDBSP(),
                                 schema,
                                 newMaintenanceConfig,
                                 storeConfig,
                                 ThreadingServiceConfig::make(_bootstrapConfig->getProtonConfig()),
                                 build_alloc_config(_bootstrapConfig->getProtonConfig(), _docTypeName),
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
DocumentDBConfigHelper::nextGeneration(FNET_Transport & transport, vespalib::duration timeout)
{
    ConfigSnapshot snapshot(_retriever->getBootstrapConfigs(timeout));
    if (snapshot.empty())
        return false;
    _mgr.update(transport, snapshot);
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
