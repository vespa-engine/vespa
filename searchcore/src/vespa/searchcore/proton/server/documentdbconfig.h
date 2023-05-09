// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "document_db_maintenance_config.h"
#include "threading_service_config.h"
#include <vespa/searchcore/proton/common/alloc_config.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/searchlib/docstore/logdocumentstore.h>
#include <vespa/searchlib/fef/onnx_models.h>
#include <vespa/searchlib/fef/ranking_constants.h>
#include <vespa/searchlib/fef/ranking_expressions.h>
#include <vespa/searchcommon/common/schema.h>
#include <vespa/document/config/documenttypes_config_fwd.h>

#include <vespa/config/retriever/configkeyset.h>
#include <vespa/config/retriever/configsnapshot.h>

namespace vespa::config::search::internal {
    class InternalSummaryType;
    class InternalRankProfilesType;
    class InternalAttributesType;
    class InternalIndexschemaType;
    class InternalImportedFieldsType;
}
namespace vespa::config::search::summary { namespace internal { class InternalJuniperrcType; } }

namespace document {
    class DocumentTypeRepo;
    class DocumentType;
}

namespace proton {

class DocumentDBConfig
{
public:
    class ComparisonResult
    {
    public:
        bool rankProfilesChanged;
        bool rankingConstantsChanged;
        bool rankingExpressionsChanged;
        bool onnxModelsChanged;
        bool indexschemaChanged;
        bool attributesChanged;
        bool summaryChanged;
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
        bool alloc_config_changed;

        ComparisonResult();
        ComparisonResult &setRankProfilesChanged(bool val) { rankProfilesChanged = val; return *this; }
        ComparisonResult &setRankingConstantsChanged(bool val) { rankingConstantsChanged = val; return *this; }
        ComparisonResult &setRankingExpressionsChanged(bool val) { rankingExpressionsChanged = val; return *this; }
        ComparisonResult &setOnnxModelsChanged(bool val) { onnxModelsChanged = val; return *this; }
        ComparisonResult &setIndexschemaChanged(bool val) { indexschemaChanged = val; return *this; }
        ComparisonResult &setAttributesChanged(bool val) { attributesChanged = val; return *this; }
        ComparisonResult &setSummaryChanged(bool val) { summaryChanged = val; return *this; }
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
        ComparisonResult &set_alloc_config_changed(bool val) { alloc_config_changed = val; return *this; }
    };

    using SP = std::shared_ptr<DocumentDBConfig>;
    using IndexschemaConfig = const vespa::config::search::internal::InternalIndexschemaType;
    using IndexschemaConfigSP = std::shared_ptr<IndexschemaConfig>;
    using AttributesConfig = const vespa::config::search::internal::InternalAttributesType;
    using AttributesConfigSP = std::shared_ptr<AttributesConfig>;
    using RankProfilesConfig = const vespa::config::search::internal::InternalRankProfilesType;
    using RankProfilesConfigSP = std::shared_ptr<RankProfilesConfig>;
    using RankingConstants = search::fef::RankingConstants;
    using RankingExpressions = search::fef::RankingExpressions;
    using OnnxModels = search::fef::OnnxModels;
    using SummaryConfig = const vespa::config::search::internal::InternalSummaryType;
    using SummaryConfigSP = std::shared_ptr<SummaryConfig>;
    using JuniperrcConfig = const vespa::config::search::summary::internal::InternalJuniperrcType;
    using JuniperrcConfigSP = std::shared_ptr<JuniperrcConfig>;
    using DocumenttypesConfigSP = std::shared_ptr<DocumenttypesConfig>;
    using MaintenanceConfigSP = DocumentDBMaintenanceConfig::SP;
    using ImportedFieldsConfig = const vespa::config::search::internal::InternalImportedFieldsType;
    using ImportedFieldsConfigSP = std::shared_ptr<ImportedFieldsConfig>;

private:
    vespalib::string                          _configId;
    vespalib::string                          _docTypeName;
    int64_t                                   _generation;
    RankProfilesConfigSP                      _rankProfiles;
    std::shared_ptr<const RankingConstants>   _rankingConstants;
    std::shared_ptr<const RankingExpressions> _rankingExpressions;
    std::shared_ptr<const OnnxModels>         _onnxModels;
    IndexschemaConfigSP                       _indexschema;
    AttributesConfigSP                        _attributes;
    SummaryConfigSP                           _summary;
    JuniperrcConfigSP                         _juniperrc;
    DocumenttypesConfigSP                     _documenttypes;
    std::shared_ptr<const document::DocumentTypeRepo>   _repo;
    ImportedFieldsConfigSP                    _importedFields;
    search::TuneFileDocumentDB::SP            _tuneFileDocumentDB;
    search::index::Schema::SP                 _schema;
    MaintenanceConfigSP                       _maintenance;
    search::LogDocumentStore::Config          _storeConfig;
    const ThreadingServiceConfig              _threading_service_config;
    const AllocConfig                         _alloc_config;
    SP                                        _orig;
    bool                                      _delayedAttributeAspects;


    template <typename T>
    bool equals(const T * lhs, const T * rhs) const
    {
        if (lhs == nullptr) {
            return rhs == nullptr;
        }
        return rhs != nullptr && *lhs == *rhs;
    }
    template <typename T, typename Func>
    bool equals(const T *lhs, const T *rhs, Func isEqual) const
    {
        if (lhs == nullptr) {
            return rhs == nullptr;
        }
        return rhs != nullptr && isEqual(*lhs, *rhs);
    }
public:
    DocumentDBConfig(int64_t generation,
                     const RankProfilesConfigSP &rankProfiles,
                     const std::shared_ptr<const RankingConstants> &rankingConstants,
                     const std::shared_ptr<const RankingExpressions> &rankingExpressions,
                     const std::shared_ptr<const OnnxModels> &onnxModels,
                     const IndexschemaConfigSP &indexschema,
                     const AttributesConfigSP &attributes,
                     const SummaryConfigSP &summary,
                     const JuniperrcConfigSP &juniperrc,
                     const DocumenttypesConfigSP &documenttypesConfig,
                     const std::shared_ptr<const document::DocumentTypeRepo> &repo,
                     const ImportedFieldsConfigSP &importedFields,
                     const search::TuneFileDocumentDB::SP &tuneFileDocumentDB,
                     const search::index::Schema::SP &schema,
                     const DocumentDBMaintenanceConfig::SP &maintenance,
                     const search::LogDocumentStore::Config & storeConfig,
                     const ThreadingServiceConfig & threading_service_config,
                     const AllocConfig & alloc_config,
                     const vespalib::string &configId,
                     const vespalib::string &docTypeName) noexcept;

    DocumentDBConfig(const DocumentDBConfig &cfg);
    ~DocumentDBConfig();

    const vespalib::string &getConfigId() const { return _configId; }
    void setConfigId(const vespalib::string &configId) { _configId = configId; }

    const vespalib::string &getDocTypeName() const { return _docTypeName; }

    int64_t getGeneration() const { return _generation; }

    const RankProfilesConfig &getRankProfilesConfig() const { return *_rankProfiles; }
    const RankingConstants &getRankingConstants() const { return *_rankingConstants; }
    const RankingExpressions &getRankingExpressions() const { return *_rankingExpressions; }
    const OnnxModels &getOnnxModels() const { return *_onnxModels; }
    const IndexschemaConfig &getIndexschemaConfig() const { return *_indexschema; }
    const AttributesConfig &getAttributesConfig() const { return *_attributes; }
    const SummaryConfig &getSummaryConfig() const { return *_summary; }
    const JuniperrcConfig &getJuniperrcConfig() const { return *_juniperrc; }
    const DocumenttypesConfig &getDocumenttypesConfig() const { return *_documenttypes; }
    const RankProfilesConfigSP &getRankProfilesConfigSP() const { return _rankProfiles; }
    const std::shared_ptr<const RankingConstants> &getRankingConstantsSP() const { return _rankingConstants; }
    const std::shared_ptr<const RankingExpressions> &getRankingExpressionsSP() const { return _rankingExpressions; }
    const std::shared_ptr<const OnnxModels> &getOnnxModelsSP() const { return _onnxModels; }
    const IndexschemaConfigSP &getIndexschemaConfigSP() const { return _indexschema; }
    const AttributesConfigSP &getAttributesConfigSP() const { return _attributes; }
    const SummaryConfigSP &getSummaryConfigSP() const { return _summary; }
    const JuniperrcConfigSP &getJuniperrcConfigSP() const { return _juniperrc; }
    const DocumenttypesConfigSP &getDocumenttypesConfigSP() const { return _documenttypes; }
    const std::shared_ptr<const document::DocumentTypeRepo> &getDocumentTypeRepoSP() const { return _repo; }
    const document::DocumentType *getDocumentType() const;
    const ImportedFieldsConfig &getImportedFieldsConfig() const { return *_importedFields; }
    const ImportedFieldsConfigSP &getImportedFieldsConfigSP() const { return _importedFields; }
    const search::index::Schema::SP &getSchemaSP() const { return _schema; }
    const MaintenanceConfigSP &getMaintenanceConfigSP() const { return _maintenance; }
    const search::TuneFileDocumentDB::SP &getTuneFileDocumentDBSP() const { return _tuneFileDocumentDB; }
    bool getDelayedAttributeAspects() const { return _delayedAttributeAspects; }
    const ThreadingServiceConfig& get_threading_service_config() const { return _threading_service_config; }
    const AllocConfig& get_alloc_config() const { return _alloc_config; }

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

    static std::shared_ptr<search::index::Schema>
    build_schema(const AttributesConfig& attributes_config,
                 const IndexschemaConfig &indexschema_config);
};

} // namespace proton

