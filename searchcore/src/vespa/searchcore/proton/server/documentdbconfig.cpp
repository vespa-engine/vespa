// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentdbconfig.h"
#include "threading_service_config.h"
#include <vespa/config-attributes.h>
#include <vespa/config-imported-fields.h>
#include <vespa/config-rank-profiles.h>
#include <vespa/config-summary.h>
#include <vespa/searchsummary/config/config-juniperrc.h>
#include <vespa/document/config/documenttypes_config_fwd.h>
#include <vespa/document/config/config-documenttypes.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/searchcommon/common/schemaconfigurer.h>
#include <vespa/searchcore/config/config-ranking-constants.h>
#include <vespa/searchcore/config/config-onnx-models.h>
#include <vespa/searchcore/proton/attribute/attribute_aspect_delayer.h>
#include <vespa/searchcore/proton/common/alloc_config.h>
#include <vespa/searchcore/proton/common/document_type_inspector.h>
#include <vespa/searchcore/proton/common/indexschema_inspector.h>

using namespace config;
using namespace vespa::config::search::summary;
using namespace vespa::config::search;

using document::DocumentTypeRepo;
using search::TuneFileDocumentDB;
using search::index::Schema;
using search::index::SchemaBuilder;
using vespa::config::search::core::RankingConstantsConfig;
using vespa::config::search::core::OnnxModelsConfig;

namespace proton {

DocumentDBConfig::ComparisonResult::ComparisonResult()
    : rankProfilesChanged(false),
      rankingConstantsChanged(false),
      rankingExpressionsChanged(false),
      onnxModelsChanged(false),
      indexschemaChanged(false),
      attributesChanged(false),
      summaryChanged(false),
      juniperrcChanged(false),
      documenttypesChanged(false),
      documentTypeRepoChanged(false),
      importedFieldsChanged(false),
      tuneFileDocumentDBChanged(false),
      schemaChanged(false),
      maintenanceChanged(false),
      storeChanged(false),
      visibilityDelayChanged(false),
      flushChanged(false),
      alloc_config_changed(false)
{ }

DocumentDBConfig::DocumentDBConfig(
               int64_t generation,
               const RankProfilesConfigSP &rankProfiles,
               const RankingConstants::SP &rankingConstants,
               const RankingExpressions::SP &rankingExpressions,
               const OnnxModels::SP &onnxModels,
               const IndexschemaConfigSP &indexschema,
               const AttributesConfigSP &attributes,
               const SummaryConfigSP &summary,
               const JuniperrcConfigSP &juniperrc,
               const DocumenttypesConfigSP &documenttypes,
               const std::shared_ptr<const DocumentTypeRepo> &repo,
               const ImportedFieldsConfigSP &importedFields,
               const search::TuneFileDocumentDB::SP &tuneFileDocumentDB,
               const Schema::SP &schema,
               const DocumentDBMaintenanceConfig::SP &maintenance,
               const search::LogDocumentStore::Config & storeConfig,
               const ThreadingServiceConfig & threading_service_config,
               const AllocConfig & alloc_config,
               const vespalib::string &configId,
               const vespalib::string &docTypeName) noexcept
    : _configId(configId),
      _docTypeName(docTypeName),
      _generation(generation),
      _rankProfiles(rankProfiles),
      _rankingConstants(rankingConstants),
      _rankingExpressions(rankingExpressions),
      _onnxModels(onnxModels),
      _indexschema(indexschema),
      _attributes(attributes),
      _summary(summary),
      _juniperrc(juniperrc),
      _documenttypes(documenttypes),
      _repo(repo),
      _importedFields(importedFields),
      _tuneFileDocumentDB(tuneFileDocumentDB),
      _schema(schema),
      _maintenance(maintenance),
      _storeConfig(storeConfig),
      _threading_service_config(threading_service_config),
      _alloc_config(alloc_config),
      _orig(),
      _delayedAttributeAspects(false)
{ }


DocumentDBConfig::
DocumentDBConfig(const DocumentDBConfig &cfg)
    : _configId(cfg._configId),
      _docTypeName(cfg._docTypeName),
      _generation(cfg._generation),
      _rankProfiles(cfg._rankProfiles),
      _rankingConstants(cfg._rankingConstants),
      _rankingExpressions(cfg._rankingExpressions),
      _onnxModels(cfg._onnxModels),
      _indexschema(cfg._indexschema),
      _attributes(cfg._attributes),
      _summary(cfg._summary),
      _juniperrc(cfg._juniperrc),
      _documenttypes(cfg._documenttypes),
      _repo(cfg._repo),
      _importedFields(cfg._importedFields),
      _tuneFileDocumentDB(cfg._tuneFileDocumentDB),
      _schema(cfg._schema),
      _maintenance(cfg._maintenance),
      _storeConfig(cfg._storeConfig),
      _threading_service_config(cfg._threading_service_config),
      _alloc_config(cfg._alloc_config),
      _orig(cfg._orig),
      _delayedAttributeAspects(false)
{ }

DocumentDBConfig::~DocumentDBConfig() = default;

bool
DocumentDBConfig::operator==(const DocumentDBConfig & rhs) const
{
    return equals<RankProfilesConfig>(_rankProfiles.get(), rhs._rankProfiles.get()) &&
           equals<RankingConstants>(_rankingConstants.get(), rhs._rankingConstants.get()) &&
           equals<RankingExpressions>(_rankingExpressions.get(), rhs._rankingExpressions.get()) &&
           equals<OnnxModels>(_onnxModels.get(), rhs._onnxModels.get()) &&
           equals<IndexschemaConfig>(_indexschema.get(), rhs._indexschema.get()) &&
           equals<AttributesConfig>(_attributes.get(), rhs._attributes.get()) &&
           equals<SummaryConfig>(_summary.get(), rhs._summary.get()) &&
           equals<JuniperrcConfig>(_juniperrc.get(), rhs._juniperrc.get()) &&
           equals<DocumenttypesConfig>(_documenttypes.get(), rhs._documenttypes.get()) &&
           _repo.get() == rhs._repo.get() &&
           equals<ImportedFieldsConfig >(_importedFields.get(), rhs._importedFields.get()) &&
           equals<TuneFileDocumentDB>(_tuneFileDocumentDB.get(), rhs._tuneFileDocumentDB.get()) &&
           equals<Schema>(_schema.get(), rhs._schema.get()) &&
           equals<DocumentDBMaintenanceConfig>(_maintenance.get(), rhs._maintenance.get()) &&
           (_storeConfig == rhs._storeConfig) &&
           (_threading_service_config == rhs._threading_service_config) &&
           (_alloc_config == rhs._alloc_config);
}


DocumentDBConfig::ComparisonResult
DocumentDBConfig::compare(const DocumentDBConfig &rhs) const
{
    ComparisonResult retval;
    retval.rankProfilesChanged = !equals<RankProfilesConfig>(_rankProfiles.get(), rhs._rankProfiles.get());
    retval.rankingConstantsChanged = !equals<RankingConstants>(_rankingConstants.get(), rhs._rankingConstants.get());
    retval.rankingExpressionsChanged = !equals<RankingExpressions>(_rankingExpressions.get(), rhs._rankingExpressions.get());
    retval.onnxModelsChanged = !equals<OnnxModels>(_onnxModels.get(), rhs._onnxModels.get());
    retval.indexschemaChanged = !equals<IndexschemaConfig>(_indexschema.get(), rhs._indexschema.get());
    retval.attributesChanged = !equals<AttributesConfig>(_attributes.get(), rhs._attributes.get());
    retval.summaryChanged = !equals<SummaryConfig>(_summary.get(), rhs._summary.get());
    retval.juniperrcChanged = !equals<JuniperrcConfig>(_juniperrc.get(), rhs._juniperrc.get());
    retval.documenttypesChanged = !equals<DocumenttypesConfig>(_documenttypes.get(), rhs._documenttypes.get());
    retval.documentTypeRepoChanged = _repo.get() != rhs._repo.get();
    retval.importedFieldsChanged = !equals<ImportedFieldsConfig >(_importedFields.get(), rhs._importedFields.get());
    retval.tuneFileDocumentDBChanged = !equals<TuneFileDocumentDB>(_tuneFileDocumentDB.get(), rhs._tuneFileDocumentDB.get());
    retval.schemaChanged = !equals<Schema>(_schema.get(), rhs._schema.get());
    retval.maintenanceChanged = !equals<DocumentDBMaintenanceConfig>(_maintenance.get(), rhs._maintenance.get());
    retval.storeChanged = (_storeConfig != rhs._storeConfig);
    retval.visibilityDelayChanged = (_maintenance->getVisibilityDelay() != rhs._maintenance->getVisibilityDelay());
    retval.flushChanged = !equals<DocumentDBMaintenanceConfig>(_maintenance.get(), rhs._maintenance.get(), [](const auto &l, const auto &r) { return l.getFlushConfig() == r.getFlushConfig(); });
    retval.alloc_config_changed = (_alloc_config != rhs._alloc_config);
    return retval;
}


bool
DocumentDBConfig::valid() const
{
    return _rankProfiles &&
           _rankingConstants &&
           _rankingExpressions &&
           _onnxModels &&
           _indexschema &&
           _attributes &&
           _summary &&
           _juniperrc &&
           _documenttypes &&
           _repo &&
           _importedFields &&
           _tuneFileDocumentDB &&
           _schema &&
           _maintenance;
}

namespace
{

template <class Config>
std::shared_ptr<Config>
emptyConfig(std::shared_ptr<Config> config)
{
    std::shared_ptr<Config> empty(std::make_shared<Config>());
    
    if (!config || *config != *empty) {
        return empty;
    }
    return config;
}

template <>
std::shared_ptr<SummaryConfig>
emptyConfig(std::shared_ptr<SummaryConfig> config)
{
    auto  empty(std::make_shared<SummaryConfigBuilder>());
    if (config) {
        empty->usev8geopositions = config->usev8geopositions;
    }
    empty->defaultsummaryid = 0;
    empty->classes.emplace_back();
    auto& default_summary_class = empty->classes.back();
    default_summary_class.id = 0;
    default_summary_class.name = "default";
    if (!config || *config != *empty) {
        return empty;
    }
    return config;
}

}


DocumentDBConfig::SP
DocumentDBConfig::makeReplayConfig(const SP & orig)
{
    const DocumentDBConfig &o = *orig;

    auto replay_summary_config = emptyConfig(o._summary);
    auto replay_schema = build_schema(*o._attributes, *o._indexschema);
    if (*replay_schema == *o._schema) {
        replay_schema = o._schema;
    }
    SP ret = std::make_shared<DocumentDBConfig>(
                o._generation,
                emptyConfig(o._rankProfiles),
                std::make_shared<RankingConstants>(),
                std::make_shared<RankingExpressions>(),
                std::make_shared<OnnxModels>(),
                o._indexschema,
                o._attributes,
                replay_summary_config,
                emptyConfig(o._juniperrc),
                o._documenttypes,
                o._repo,
                std::make_shared<ImportedFieldsConfig>(),
                o._tuneFileDocumentDB,
                replay_schema,
                o._maintenance,
                o._storeConfig,
                o._threading_service_config,
                o._alloc_config,
                o._configId,
                o._docTypeName);
    ret->_orig = orig;
    return ret;
}


DocumentDBConfig::SP
DocumentDBConfig::getOriginalConfig() const
{
    return _orig;
}


DocumentDBConfig::SP
DocumentDBConfig::preferOriginalConfig(const SP & self)
{
    return (self && self->_orig) ? self->_orig : self;
}


DocumentDBConfig::SP
DocumentDBConfig::newFromAttributesConfig(const AttributesConfigSP &attributes) const
{
    return std::make_shared<DocumentDBConfig>(
            _generation,
            _rankProfiles,
            _rankingConstants,
            _rankingExpressions,
            _onnxModels,
            _indexschema,
            attributes,
            _summary,
            _juniperrc,
            _documenttypes,
            _repo,
            _importedFields,
            _tuneFileDocumentDB,
            _schema,
            _maintenance,
            _storeConfig,
            _threading_service_config,
            _alloc_config,
            _configId,
            _docTypeName);
}

DocumentDBConfig::SP
DocumentDBConfig::makeDelayedAttributeAspectConfig(const SP &newCfg, const DocumentDBConfig &oldCfg)
{
    const DocumentDBConfig &n = *newCfg;
    AttributeAspectDelayer attributeAspectDelayer;
    DocumentTypeInspector inspector(*oldCfg.getDocumentType(), *n.getDocumentType());
    IndexschemaInspector oldIndexschemaInspector(oldCfg.getIndexschemaConfig());
    attributeAspectDelayer.setup(oldCfg.getAttributesConfig(),
                                 n.getAttributesConfig(), n.getSummaryConfig(),
                                 oldIndexschemaInspector, inspector);
    bool attributes_config_changed = (n.getAttributesConfig() != *attributeAspectDelayer.getAttributesConfig());
    bool summary_config_changed = (n.getSummaryConfig() != *attributeAspectDelayer.getSummaryConfig());
    bool delayedAttributeAspects = (attributes_config_changed || summary_config_changed);
    if (!delayedAttributeAspects) {
        return newCfg;
    }
    auto result = std::make_shared<DocumentDBConfig>
                  (n._generation,
                   n._rankProfiles,
                   n._rankingConstants,
                   n._rankingExpressions,
                   n._onnxModels,
                   n._indexschema,
                   (attributes_config_changed ? attributeAspectDelayer.getAttributesConfig() : n._attributes),
                   (summary_config_changed ? attributeAspectDelayer.getSummaryConfig() : n._summary),
                   n._juniperrc,
                   n._documenttypes,
                   n._repo,
                   n._importedFields,
                   n._tuneFileDocumentDB,
                   n._schema,
                   n._maintenance,
                   n._storeConfig,
                   n._threading_service_config,
                   n._alloc_config,
                   n._configId,
                   n._docTypeName);
    result->_delayedAttributeAspects = true;
    return result;
}

const document::DocumentType *
DocumentDBConfig::getDocumentType() const
{
    return _repo->getDocumentType(getDocTypeName());
}

std::shared_ptr<Schema>
DocumentDBConfig::build_schema(const AttributesConfig& attributes_config,
                               const IndexschemaConfig &indexschema_config)
{
    auto schema = std::make_shared<Schema>();
    SchemaBuilder::build(attributes_config, *schema);
    SchemaBuilder::build(indexschema_config, *schema);
    return schema;
}

} // namespace proton
