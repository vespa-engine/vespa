// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentdbconfig.h"
#include <vespa/config-attributes.h>
#include <vespa/config-imported-fields.h>
#include <vespa/config-indexschema.h>
#include <vespa/config-rank-profiles.h>
#include <vespa/config-summary.h>
#include <vespa/config-summarymap.h>
#include <vespa/searchsummary/config/config-juniperrc.h>
#include <vespa/document/config/config-documenttypes.h>
#include <vespa/searchcore/config/config-ranking-constants.h>
#include <vespa/searchcore/proton/attribute/attribute_aspect_delayer.h>
#include <vespa/searchcore/proton/common/document_type_inspector.h>
#include <vespa/searchcore/proton/common/indexschema_inspector.h>

using namespace config;
using namespace vespa::config::search::summary;
using namespace vespa::config::search;

using document::DocumentTypeRepo;
using document::DocumenttypesConfig;
using search::TuneFileDocumentDB;
using search::index::Schema;
using vespa::config::search::SummarymapConfig;
using vespa::config::search::core::RankingConstantsConfig;

namespace proton {

DocumentDBConfig::ComparisonResult::ComparisonResult()
    : rankProfilesChanged(false),
      rankingConstantsChanged(false),
      indexschemaChanged(false),
      attributesChanged(false),
      summaryChanged(false),
      summarymapChanged(false),
      juniperrcChanged(false),
      documenttypesChanged(false),
      documentTypeRepoChanged(false),
      importedFieldsChanged(false),
      tuneFileDocumentDBChanged(false),
      schemaChanged(false),
      maintenanceChanged(false),
      storeChanged(false),
      visibilityDelayChanged(false),
      flushChanged(false)
{ }

DocumentDBConfig::DocumentDBConfig(
               int64_t generation,
               const RankProfilesConfigSP &rankProfiles,
               const RankingConstants::SP &rankingConstants,
               const IndexschemaConfigSP &indexschema,
               const AttributesConfigSP &attributes,
               const SummaryConfigSP &summary,
               const SummarymapConfigSP &summarymap,
               const JuniperrcConfigSP &juniperrc,
               const DocumenttypesConfigSP &documenttypes,
               const std::shared_ptr<const DocumentTypeRepo> &repo,
               const ImportedFieldsConfigSP &importedFields,
               const search::TuneFileDocumentDB::SP &tuneFileDocumentDB,
               const Schema::SP &schema,
               const DocumentDBMaintenanceConfig::SP &maintenance,
               const search::LogDocumentStore::Config & storeConfig,
               const vespalib::string &configId,
               const vespalib::string &docTypeName)
    : _configId(configId),
      _docTypeName(docTypeName),
      _generation(generation),
      _rankProfiles(rankProfiles),
      _rankingConstants(rankingConstants),
      _indexschema(indexschema),
      _attributes(attributes),
      _summary(summary),
      _summarymap(summarymap),
      _juniperrc(juniperrc),
      _documenttypes(documenttypes),
      _repo(repo),
      _importedFields(importedFields),
      _tuneFileDocumentDB(tuneFileDocumentDB),
      _schema(schema),
      _maintenance(maintenance),
      _storeConfig(storeConfig),
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
      _indexschema(cfg._indexschema),
      _attributes(cfg._attributes),
      _summary(cfg._summary),
      _summarymap(cfg._summarymap),
      _juniperrc(cfg._juniperrc),
      _documenttypes(cfg._documenttypes),
      _repo(cfg._repo),
      _importedFields(cfg._importedFields),
      _tuneFileDocumentDB(cfg._tuneFileDocumentDB),
      _schema(cfg._schema),
      _maintenance(cfg._maintenance),
      _storeConfig(cfg._storeConfig),
      _orig(cfg._orig),
      _delayedAttributeAspects(false)
{ }

DocumentDBConfig::~DocumentDBConfig() { }

bool
DocumentDBConfig::operator==(const DocumentDBConfig & rhs) const
{
    return equals<RankProfilesConfig>(_rankProfiles.get(), rhs._rankProfiles.get()) &&
           equals<RankingConstants>(_rankingConstants.get(), rhs._rankingConstants.get()) &&
           equals<IndexschemaConfig>(_indexschema.get(), rhs._indexschema.get()) &&
           equals<AttributesConfig>(_attributes.get(), rhs._attributes.get()) &&
           equals<SummaryConfig>(_summary.get(), rhs._summary.get()) &&
           equals<SummarymapConfig>(_summarymap.get(), rhs._summarymap.get()) &&
           equals<JuniperrcConfig>(_juniperrc.get(), rhs._juniperrc.get()) &&
           equals<DocumenttypesConfig>(_documenttypes.get(), rhs._documenttypes.get()) &&
           _repo.get() == rhs._repo.get() &&
           equals<ImportedFieldsConfig >(_importedFields.get(), rhs._importedFields.get()) &&
           equals<TuneFileDocumentDB>(_tuneFileDocumentDB.get(), rhs._tuneFileDocumentDB.get()) &&
           equals<Schema>(_schema.get(), rhs._schema.get()) &&
           equals<DocumentDBMaintenanceConfig>(_maintenance.get(), rhs._maintenance.get()) &&
           _storeConfig == rhs._storeConfig;
}


DocumentDBConfig::ComparisonResult
DocumentDBConfig::compare(const DocumentDBConfig &rhs) const
{
    ComparisonResult retval;
    retval.rankProfilesChanged = !equals<RankProfilesConfig>(_rankProfiles.get(), rhs._rankProfiles.get());
    retval.rankingConstantsChanged = !equals<RankingConstants>(_rankingConstants.get(), rhs._rankingConstants.get());
    retval.indexschemaChanged = !equals<IndexschemaConfig>(_indexschema.get(), rhs._indexschema.get());
    retval.attributesChanged = !equals<AttributesConfig>(_attributes.get(), rhs._attributes.get());
    retval.summaryChanged = !equals<SummaryConfig>(_summary.get(), rhs._summary.get());
    retval.summarymapChanged = !equals<SummarymapConfig>(_summarymap.get(), rhs._summarymap.get());
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
    return retval;
}


bool
DocumentDBConfig::valid() const
{
    return _rankProfiles &&
           _rankingConstants &&
           _indexschema &&
           _attributes &&
           _summary &&
           _summarymap &&
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

}


DocumentDBConfig::SP
DocumentDBConfig::makeReplayConfig(const SP & orig)
{
    const DocumentDBConfig &o = *orig;
    
    SP ret = std::make_shared<DocumentDBConfig>(
                o._generation,
                emptyConfig(o._rankProfiles),
                std::make_shared<RankingConstants>(),
                o._indexschema,
                o._attributes,
                o._summary,
                std::make_shared<SummarymapConfig>(),
                o._juniperrc,
                o._documenttypes,
                o._repo,
                std::make_shared<ImportedFieldsConfig>(),
                o._tuneFileDocumentDB,
                o._schema,
                o._maintenance,
                o._storeConfig,
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
            _indexschema,
            attributes,
            _summary,
            _summarymap,
            _juniperrc,
            _documenttypes,
            _repo,
            _importedFields,
            _tuneFileDocumentDB,
            _schema,
            _maintenance,
            _storeConfig,
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
    attributeAspectDelayer.setup(oldCfg.getAttributesConfig(), oldCfg.getSummarymapConfig(),
                                 n.getAttributesConfig(), n.getSummarymapConfig(),
                                 oldIndexschemaInspector, inspector);
    bool delayedAttributeAspects = (n.getAttributesConfig() != *attributeAspectDelayer.getAttributesConfig()) ||
                                   (n.getSummarymapConfig() != *attributeAspectDelayer.getSummarymapConfig());
    if (!delayedAttributeAspects) {
        return newCfg;
    }
    auto result = std::make_shared<DocumentDBConfig>
                  (n._generation,
                   n._rankProfiles,
                   n._rankingConstants,
                   n._indexschema,
                   attributeAspectDelayer.getAttributesConfig(),
                   n._summary,
                   attributeAspectDelayer.getSummarymapConfig(),
                   n._juniperrc,
                   n._documenttypes,
                   n._repo,
                   n._importedFields,
                   n._tuneFileDocumentDB,
                   n._schema,
                   n._maintenance,
                   n._storeConfig,
                   n._configId,
                   n._docTypeName);
    result->_delayedAttributeAspects = true;
    return result;
}

} // namespace proton
