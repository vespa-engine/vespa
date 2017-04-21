// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "document_db_maintenance_config.h"
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchcore/config/config-ranking-constants.h>
#include <vespa/searchcore/proton/matching/ranking_constants.h>
#include <vespa/config/retriever/configkeyset.h>
#include <vespa/config/retriever/configsnapshot.h>

namespace vespa {
    namespace config {
        namespace search {
            namespace internal {
                class InternalSummaryType;
                class InternalSummarymapType;
                class InternalRankProfilesType;
                class InternalAttributesType;
                class InternalIndexschemaType;
                class InternalImportedFieldsType;
            }
            namespace summary { namespace internal { class InternalJuniperrcType; } }
        }
    }
}

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
        bool _documenttypesChanged;
        bool _documentTypeRepoChanged;
        bool _importedFieldsChanged;
        bool _tuneFileDocumentDBChanged;
        bool _schemaChanged;
        bool _maintenanceChanged;

        ComparisonResult();
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
    vespalib::string               _configId;
    vespalib::string               _docTypeName;
    int64_t                        _generation;
    RankProfilesConfigSP           _rankProfiles;
    RankingConstants::SP           _rankingConstants;
    IndexschemaConfigSP            _indexschema;
    AttributesConfigSP             _attributes;
    SummaryConfigSP                _summary;
    SummarymapConfigSP             _summarymap;
    JuniperrcConfigSP              _juniperrc;
    DocumenttypesConfigSP          _documenttypes;
    document::DocumentTypeRepo::SP _repo;
    ImportedFieldsConfigSP         _importedFields;
    search::TuneFileDocumentDB::SP _tuneFileDocumentDB;
    search::index::Schema::SP      _schema;
    MaintenanceConfigSP            _maintenance;
    config::ConfigSnapshot         _extraConfigs;
    SP _orig;
    bool                           _delayedAttributeAspects;


    template <typename T>
    bool equals(const T * lhs, const T * rhs) const
    {
        if (lhs == NULL) {
            return rhs == NULL;
        }
        return rhs != NULL && *lhs == *rhs;
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
                     const vespalib::string &configId,
                     const vespalib::string &docTypeName,
                     const config::ConfigSnapshot &extraConfig = config::ConfigSnapshot());

    DocumentDBConfig(const DocumentDBConfig &cfg);
    ~DocumentDBConfig();

    const vespalib::string &getConfigId() const { return _configId; }
    void setConfigId(const vespalib::string &configId) { _configId = configId; }

    const vespalib::string &getDocTypeName() const { return _docTypeName; }

    int64_t getGeneration(void) const { return _generation; }

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

    const config::ConfigSnapshot &getExtraConfigs() const { return _extraConfigs; }
    void setExtraConfigs(const config::ConfigSnapshot &extraConfigs) { _extraConfigs = extraConfigs; }

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

    /**
     * Create config with delayed attribute aspect changes if they require
     * reprocessing.
     */
    static SP makeDelayedAttributeAspectConfig(const SP &orig, const DocumentDBConfig &old);
};

} // namespace proton

