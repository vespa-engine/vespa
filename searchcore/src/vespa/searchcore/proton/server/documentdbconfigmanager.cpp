// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentdbconfigmanager.h"
#include "bootstrapconfig.h"
#include <vespa/searchcore/proton/common/hw_info.h>
#include <vespa/searchcore/config/config-ranking-constants.h>
#include <vespa/config-imported-fields.h>
#include <vespa/config-rank-profiles.h>
#include <vespa/config-summarymap.h>
#include <vespa/config/file_acquirer/file_acquirer.h>
#include <vespa/config/helper/legacy.h>
#include <vespa/searchcommon/common/schemaconfigurer.h>
#include <vespa/searchlib/index/schemautil.h>
#include <vespa/searchsummary/config/config-juniperrc.h>
#include <vespa/vespalib/time/time_box.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.documentdbconfigmanager");

using namespace config;
using namespace vespa::config::search::core;
using namespace vespa::config::search::summary;
using namespace vespa::config::search;

using document::DocumentTypeRepo;
using fastos::TimeStamp;
using search::TuneFileDocumentDB;
using search::index::Schema;
using search::index::SchemaBuilder;
using proton::matching::RankingConstants;
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

    TimeStamp visibilityDelay;
    bool isDocumentTypeGlobal = false;
    // Use document type to find document db config in proton config
    uint32_t index;
    for (index = 0; index < proton.documentdb.size(); ++index) {
        const DdbConfig &ddbConfig = proton.documentdb[index];
        if (docTypeName == ddbConfig.inputdoctypename)
            break;
    }
    double pruneRemovedDocumentsAge = proton.pruneremoveddocumentsage;
    double pruneRemovedDocumentsInterval = (proton.pruneremoveddocumentsinterval == 0) ?
                                           (pruneRemovedDocumentsAge / 100) : proton.pruneremoveddocumentsinterval;

    if (index < proton.documentdb.size()) {
        const DdbConfig &ddbConfig = proton.documentdb[index];
        visibilityDelay = TimeStamp::Seconds(std::min(proton.maxvisibilitydelay, ddbConfig.visibilitydelay));
        isDocumentTypeGlobal = ddbConfig.global;
    }
    return std::make_shared<DocumentDBMaintenanceConfig>(
            DocumentDBPruneRemovedDocumentsConfig(
                    pruneRemovedDocumentsInterval,
                    pruneRemovedDocumentsAge),
            DocumentDBHeartBeatConfig(),
            proton.grouping.sessionmanager.pruning.interval,
            visibilityDelay,
            DocumentDBLidSpaceCompactionConfig(
                    proton.lidspacecompaction.interval,
                    proton.lidspacecompaction.allowedlidbloat,
                    proton.lidspacecompaction.allowedlidbloatfactor,
                    isDocumentTypeGlobal),
            AttributeUsageFilterConfig(
                    proton.writefilter.attribute.enumstorelimit,
                    proton.writefilter.attribute.multivaluelimit),
            proton.writefilter.sampleinterval,
            BlockableMaintenanceJobConfig(
                    proton.maintenancejobs.resourcelimitfactor,
                    proton.maintenancejobs.maxoutstandingmoveops),
            DocumentDBFlushConfig(
                    proton.index.maxflushed,
                    proton.index.maxflushedretired));
}

template<typename T>
CompressionConfig
deriveCompression(const T & config) {
    CompressionConfig compression;
    if (config.type == T::LZ4) {
        compression.type = CompressionConfig::LZ4;
    } else if (config.type == T::ZSTD) {
        compression.type = CompressionConfig::ZSTD;
    }
    compression.compressionLevel = config.level;
    return compression;
}

DocumentStore::Config
getStoreConfig(const ProtonConfig::Summary::Cache & cache, const HwInfo & hwInfo)
{
    size_t maxBytes = (cache.maxbytes < 0)
                      ? (hwInfo.memory().sizeBytes()*std::min(50l, -cache.maxbytes))/100l
                      : cache.maxbytes;
    return DocumentStore::Config(deriveCompression(cache.compression), maxBytes, cache.initialentries).allowVisitCaching(cache.allowvisitcaching);
}

LogDocumentStore::Config
deriveConfig(const ProtonConfig::Summary & summary, const ProtonConfig::Flush::Memory & flush, const HwInfo & hwInfo) {
    DocumentStore::Config config(getStoreConfig(summary.cache, hwInfo));
    const ProtonConfig::Summary::Log & log(summary.log);
    const ProtonConfig::Summary::Log::Chunk & chunk(log.chunk);
    WriteableFileChunk::Config fileConfig(deriveCompression(chunk.compression), chunk.maxbytes);
    LogDataStore::Config logConfig;
    logConfig.setMaxFileSize(log.maxfilesize)
            .setMaxDiskBloatFactor(std::min(flush.diskbloatfactor, flush.each.diskbloatfactor))
            .setMaxBucketSpread(log.maxbucketspread).setMinFileSizeFactor(log.minfilesizefactor)
            .compact2ActiveFile(log.compact2activefile).compactCompression(deriveCompression(log.compact.compression))
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

}

void
DocumentDBConfigManager::update(const ConfigSnapshot &snapshot, const HwInfo & hwInfo)
{
    using RankProfilesConfigSP = DocumentDBConfig::RankProfilesConfigSP;
    using RankingConstantsConfigSP = std::shared_ptr<vespa::config::search::core::RankingConstantsConfig>;
    using IndexschemaConfigSP = DocumentDBConfig::IndexschemaConfigSP;
    using AttributesConfigSP = DocumentDBConfig::AttributesConfigSP;
    using SummaryConfigSP = DocumentDBConfig::SummaryConfigSP;
    using SummarymapConfigSP = DocumentDBConfig::SummarymapConfigSP;
    using JuniperrcConfigSP = DocumentDBConfig::JuniperrcConfigSP;
    using ImportedFieldsConfigSP = DocumentDBConfig::ImportedFieldsConfigSP;
    using MaintenanceConfigSP = DocumentDBConfig::MaintenanceConfigSP;

    DocumentDBConfig::SP current = _pendingConfigSnapshot;
    RankProfilesConfigSP newRankProfilesConfig;
    matching::RankingConstants::SP newRankingConstants;
    IndexschemaConfigSP newIndexschemaConfig;
    AttributesConfigSP newAttributesConfig;
    SummaryConfigSP newSummaryConfig;
    SummarymapConfigSP newSummarymapConfig;
    JuniperrcConfigSP newJuniperrcConfig;
    ImportedFieldsConfigSP newImportedFieldsConfig;
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
        newIndexschemaConfig = current->getIndexschemaConfigSP();
        newAttributesConfig = current->getAttributesConfigSP();
        newSummaryConfig = current->getSummaryConfigSP();
        newSummarymapConfig = current->getSummarymapConfigSP();
        newJuniperrcConfig = current->getJuniperrcConfigSP();
        newImportedFieldsConfig = current->getImportedFieldsConfigSP();
        oldMaintenanceConfig = current->getMaintenanceConfigSP();
        currentGeneration = current->getGeneration();
    }

    if (snapshot.isChanged<RankProfilesConfig>(_configId, currentGeneration)) {
        newRankProfilesConfig =
            RankProfilesConfigSP(
                    snapshot.getConfig<RankProfilesConfig>(_configId).
                    release());
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
    if (snapshot.isChanged<IndexschemaConfig>(_configId, currentGeneration)) {
        std::unique_ptr<IndexschemaConfig> indexschemaConfig = snapshot.getConfig<IndexschemaConfig>(_configId);
        search::index::Schema schema;
        search::index::SchemaBuilder::build(*indexschemaConfig, schema);
        if (!search::index::SchemaUtil::validateSchema(schema)) {
            LOG(error, "Cannot use bad index schema, validation failed");
            abort();
        }
        newIndexschemaConfig = IndexschemaConfigSP(indexschemaConfig.release());
    }
    if (snapshot.isChanged<AttributesConfig>(_configId, currentGeneration)) {
        newAttributesConfig = AttributesConfigSP(snapshot.getConfig<AttributesConfig>(_configId).release());
    }
    if (snapshot.isChanged<SummaryConfig>(_configId, currentGeneration)) {
        newSummaryConfig = SummaryConfigSP(snapshot.getConfig<SummaryConfig>(_configId).release());
    }
    if (snapshot.isChanged<SummarymapConfig>(_configId, currentGeneration)) {
        newSummarymapConfig = SummarymapConfigSP(snapshot.getConfig<SummarymapConfig>(_configId).release());
    }
    if (snapshot.isChanged<JuniperrcConfig>(_configId, currentGeneration)) {
        newJuniperrcConfig = JuniperrcConfigSP(snapshot.getConfig<JuniperrcConfig>(_configId).release());
    }
    if (snapshot.isChanged<ImportedFieldsConfig>(_configId, currentGeneration)) {
        newImportedFieldsConfig = ImportedFieldsConfigSP(snapshot.getConfig<ImportedFieldsConfig>(_configId).release());
    }

    Schema::SP schema(buildSchema(*newAttributesConfig, *newSummaryConfig, *newIndexschemaConfig));
    newMaintenanceConfig = buildMaintenanceConfig(_bootstrapConfig, _docTypeName);
    search::LogDocumentStore::Config storeConfig = buildStoreConfig(_bootstrapConfig->getProtonConfig(), hwInfo);
    if (newMaintenanceConfig && oldMaintenanceConfig && *newMaintenanceConfig == *oldMaintenanceConfig) {
        newMaintenanceConfig = oldMaintenanceConfig;
    }
    DocumentDBConfig::SP newSnapshot(
            new DocumentDBConfig(generation,
                                 newRankProfilesConfig,
                                 newRankingConstants,
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
                                 _configId,
                                 _docTypeName));
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

DocumentDBConfigManager::~DocumentDBConfigManager() { }

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
    : _hwInfo(std::make_unique<HwInfo>()),
      _mgr("", docTypeName),
      _retriever(make_unique<ConfigRetriever>(_mgr.createConfigKeySet(), make_shared<ConfigContext>(spec)))
{ }

DocumentDBConfigHelper::~DocumentDBConfigHelper() = default;

bool
DocumentDBConfigHelper::nextGeneration(int timeoutInMillis)
{
    ConfigSnapshot snapshot(_retriever->getBootstrapConfigs(timeoutInMillis));
    if (snapshot.empty())
        return false;
    _mgr.update(snapshot, *_hwInfo);
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
