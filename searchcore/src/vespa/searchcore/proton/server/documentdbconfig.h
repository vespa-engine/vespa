// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "document_db_maintenance_config.h"
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchcore/proton/matching/ranking_constants.h>
#include <vespa/config/retriever/configkeyset.h>
#include <vespa/config/retriever/configsnapshot.h>
#include <vespa/searchlib/docstore/logdocumentstore.h>

namespace vespa::config::search::internal {
    class InternalSummaryType;
    class InternalSummarymapType;
    class InternalRankProfilesType;
    class InternalAttributesType;
    class InternalIndexschemaType;
    class InternalImportedFieldsType;
}
namespace vespa::config::search::summary { namespace internal { class InternalJuniperrcType; } }

namespace document { namespace internal { class InternalDocumenttypesType; } }

namespace proton {

class DocumentDBConfig
{
public:
    class ComparisonResult
    {
    public:
        bool rankProfilesChanged;
        bool rankingConstantsChanged;
        bool indexschemaChanged;
        bool attributesChanged;
        bool summaryChanged;
        bool summarymapChanged;
        bool juniperrcChanged;
        bool documenttypesChanged;
        bool documentTypeRepoChanged;
        bool importedFieldsChanged;
        bool tuneFileDocumentDBChanged;
        bool schemaChanged;
        bool maintenanceChanged;
        bool storeChanged;
        bool visibilityDelayChanged;
        bool flushChanged;

        ComparisonResult();
        ComparisonResult &setRankProfilesChanged(bool val) { rankProfilesChanged = val; return *this; }
        ComparisonResult &setRankingConstantsChanged(bool val) { rankingConstantsChanged = val; return *this; }
        ComparisonResult &setIndexschemaChanged(bool val) { indexschemaChanged = val; return *this; }
        ComparisonResult &setAttributesChanged(bool val) { attributesChanged = val; return *this; }
        ComparisonResult &setSummaryChanged(bool val) { summaryChanged = val; return *this; }
        ComparisonResult &setSummarymapChanged(bool val) { summarymapChanged = val; return *this; }
        ComparisonResult &setJuniperrcChanged(bool val) { juniperrcChanged = val; return *this; }
        ComparisonResult &setDocumenttypesChanged(bool val) { documenttypesChanged = val; return *this; }
        ComparisonResult &setDocumentTypeRepoChanged(bool val) { documentTypeRepoChanged = val; return *this; }
        ComparisonResult &setImportedFieldsChanged(bool val) { importedFieldsChanged = val; return *this; }
        ComparisonResult &setTuneFileDocumentDBChanged(bool val) { tuneFileDocumentDBChanged = val; return *this; }
        ComparisonResult &setSchemaChanged(bool val) { schemaChanged = val; return *this; }
        ComparisonResult &setMaintenanceChanged(bool val) { maintenanceChanged = val; return *this; }
        ComparisonResult &setStoreChanged(bool val) { storeChanged = val; return *this; }

        ComparisonResult &setVisibilityDelayChanged(bool val) {
            visibilityDelayChanged = val;
            if (val) {
                maintenanceChanged = true;
            }
            return *this;
        }
        ComparisonResult &setFlushChanged(bool val) {
            flushChanged = val;
            if (val) {
                maintenanceChanged = true;
            }
            return *this;
        }
    };

    using SP = std::shared_ptr<DocumentDBConfig>;
    using IndexschemaConfig = const vespa::config::search::internal::InternalIndexschemaType;
    using IndexschemaConfigSP = std::shared_ptr<IndexschemaConfig>;
    using AttributesConfig = const vespa::config::search::internal::InternalAttributesType;
    using AttributesConfigSP = std::shared_ptr<AttributesConfig>;
    using RankProfilesConfig = const vespa::config::search::internal::InternalRankProfilesType;
    using RankProfilesConfigSP = std::shared_ptr<RankProfilesConfig>;
    using RankingConstants = matching::RankingConstants;
    using SummaryConfig = const vespa::config::search::internal::InternalSummaryType;
    using SummaryConfigSP = std::shared_ptr<SummaryConfig>;
    using SummarymapConfig = const vespa::config::search::internal::InternalSummarymapType;
    using SummarymapConfigSP = std::shared_ptr<SummarymapConfig>;
    using JuniperrcConfig = const vespa::config::search::summary::internal::InternalJuniperrcType;
    using JuniperrcConfigSP = std::shared_ptr<JuniperrcConfig>;
    using DocumenttypesConfig = const document::internal::InternalDocumenttypesType;
    using DocumenttypesConfigSP = std::shared_ptr<DocumenttypesConfig>;
    using MaintenanceConfigSP = DocumentDBMaintenanceConfig::SP;
    using ImportedFieldsConfig = const vespa::config::search::internal::InternalImportedFieldsType;
    using ImportedFieldsConfigSP = std::shared_ptr<ImportedFieldsConfig>;

private:
    vespalib::string                 _configId;
    vespalib::string                 _docTypeName;
    int64_t                          _generation;
    RankProfilesConfigSP             _rankProfiles;
    RankingConstants::SP             _rankingConstants;
    IndexschemaConfigSP              _indexschema;
    AttributesConfigSP               _attributes;
    SummaryConfigSP                  _summary;
    SummarymapConfigSP               _summarymap;
    JuniperrcConfigSP                _juniperrc;
    DocumenttypesConfigSP            _documenttypes;
    document::DocumentTypeRepo::SP   _repo;
    ImportedFieldsConfigSP           _importedFields;
    search::TuneFileDocumentDB::SP   _tuneFileDocumentDB;
    search::index::Schema::SP        _schema;
    MaintenanceConfigSP              _maintenance;
    search::LogDocumentStore::Config _storeConfig;
    SP                               _orig;
    bool                             _delayedAttributeAspects;


    template <typename T>
    bool equals(const T * lhs, const T * rhs) const
    {
        if (lhs == NULL) {
            return rhs == NULL;
        }
        return rhs != NULL && *lhs == *rhs;
    }
    template <typename T, typename Func>
    bool equals(const T *lhs, const T *rhs, Func isEqual) const
    {
        if (lhs == NULL) {
            return rhs == NULL;
        }
        return rhs != NULL && isEqual(*lhs, *rhs);
    }
public:
    DocumentDBConfig(int64_t generation,
                     const RankProfilesConfigSP &rankProfiles,
                     const RankingConstants::SP &rankingConstants,
                     const IndexschemaConfigSP &indexschema,
                     const AttributesConfigSP &attributes,
                     const SummaryConfigSP &summary,
                     const SummarymapConfigSP &summarymap,
                     const JuniperrcConfigSP &juniperrc,
                     const DocumenttypesConfigSP &documenttypesConfig,
                     const document::DocumentTypeRepo::SP &repo,
                     const ImportedFieldsConfigSP &importedFields,
                     const search::TuneFileDocumentDB::SP &tuneFileDocumentDB,
                     const search::index::Schema::SP &schema,
                     const DocumentDBMaintenanceConfig::SP &maintenance,
                     const search::LogDocumentStore::Config & storeConfig,
                     const vespalib::string &configId,
                     const vespalib::string &docTypeName);

    DocumentDBConfig(const DocumentDBConfig &cfg);
    ~DocumentDBConfig();

    const vespalib::string &getConfigId() const { return _configId; }
    void setConfigId(const vespalib::string &configId) { _configId = configId; }

    const vespalib::string &getDocTypeName() const { return _docTypeName; }

    int64_t getGeneration() const { return _generation; }

    const RankProfilesConfig &getRankProfilesConfig() const { return *_rankProfiles; }
    const RankingConstants &getRankingConstants() const { return *_rankingConstants; }
    const IndexschemaConfig &getIndexschemaConfig() const { return *_indexschema; }
    const AttributesConfig &getAttributesConfig() const { return *_attributes; }
    const SummaryConfig &getSummaryConfig() const { return *_summary; }
    const SummarymapConfig &getSummarymapConfig() const { return *_summarymap; }
    const JuniperrcConfig &getJuniperrcConfig() const { return *_juniperrc; }
    const DocumenttypesConfig &getDocumenttypesConfig() const { return *_documenttypes; }
    const RankProfilesConfigSP &getRankProfilesConfigSP() const { return _rankProfiles; }
    const RankingConstants::SP &getRankingConstantsSP() const { return _rankingConstants; }
    const IndexschemaConfigSP &getIndexschemaConfigSP() const { return _indexschema; }
    const AttributesConfigSP &getAttributesConfigSP() const { return _attributes; }
    const SummaryConfigSP &getSummaryConfigSP() const { return _summary; }
    const SummarymapConfigSP &getSummarymapConfigSP() const { return _summarymap; }
    const JuniperrcConfigSP &getJuniperrcConfigSP() const { return _juniperrc; }
    const DocumenttypesConfigSP &getDocumenttypesConfigSP() const { return _documenttypes; }
    const document::DocumentTypeRepo::SP &getDocumentTypeRepoSP() const { return _repo; }
    const document::DocumentType *getDocumentType() const { return _repo->getDocumentType(getDocTypeName()); }
    const ImportedFieldsConfig &getImportedFieldsConfig() const { return *_importedFields; }
    const ImportedFieldsConfigSP &getImportedFieldsConfigSP() const { return _importedFields; }
    const search::index::Schema::SP &getSchemaSP() const { return _schema; }
    const MaintenanceConfigSP &getMaintenanceConfigSP() const { return _maintenance; }
    const search::TuneFileDocumentDB::SP &getTuneFileDocumentDBSP() const { return _tuneFileDocumentDB; }
    bool getDelayedAttributeAspects() const { return _delayedAttributeAspects; }

    bool operator==(const DocumentDBConfig &rhs) const;

     /**
      * Compare this snapshot with the given one.
      */
    ComparisonResult compare(const DocumentDBConfig &rhs) const;

    bool valid() const;

    /**
     * Only keep configs needed for replay of transaction log.
     */
    static SP makeReplayConfig(const SP &orig);

    /**
     * Return original config if this is a replay config, otherwise return
     * empty shared pointer.
     */
    SP getOriginalConfig() const;

    /**
     * Return original config if cfg is a replay config, otherwise return
     * cfg.
     */
    static SP preferOriginalConfig(const SP &cfg);

    /**
     * Create modified attributes config.
     */
    SP newFromAttributesConfig(const AttributesConfigSP &attributes) const;

    const search::LogDocumentStore::Config & getStoreConfig() const { return _storeConfig; }

    /**
     * Create config with delayed attribute aspect changes if they require
     * reprocessing.
     */
    static SP makeDelayedAttributeAspectConfig(const SP &newCfg, const DocumentDBConfig &oldCfg);
};

} // namespace proton

