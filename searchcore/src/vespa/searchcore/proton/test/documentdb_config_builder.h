// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/server/documentdbconfig.h>

namespace proton::test {

/**
 * Builder for instances of DocumentDBConfig used in unit tests.
 */
class DocumentDBConfigBuilder {
private:
    using OnnxModels = DocumentDBConfig::OnnxModels;
    using RankingConstants = DocumentDBConfig::RankingConstants;
    using RankingExpressions = DocumentDBConfig::RankingExpressions;
    int64_t _generation;
    DocumentDBConfig::RankProfilesConfigSP _rankProfiles;
    std::shared_ptr<const RankingConstants> _rankingConstants;
    std::shared_ptr<const RankingExpressions> _rankingExpressions;
    std::shared_ptr<const OnnxModels> _onnxModels;
    DocumentDBConfig::IndexschemaConfigSP _indexschema;
    DocumentDBConfig::AttributesConfigSP _attributes;
    DocumentDBConfig::SummaryConfigSP _summary;
    DocumentDBConfig::JuniperrcConfigSP _juniperrc;
    DocumentDBConfig::DocumenttypesConfigSP _documenttypes;
    std::shared_ptr<const document::DocumentTypeRepo> _repo;
    DocumentDBConfig::ImportedFieldsConfigSP _importedFields;
    search::TuneFileDocumentDB::SP _tuneFileDocumentDB;
    search::index::Schema::SP _schema;
    DocumentDBConfig::MaintenanceConfigSP _maintenance;
    search::LogDocumentStore::Config _store;
    const ThreadingServiceConfig _threading_service_config;
    const AllocConfig _alloc_config;
    vespalib::string _configId;
    vespalib::string _docTypeName;

public:
    DocumentDBConfigBuilder(int64_t generation,
                            const search::index::Schema::SP &schema,
                            const vespalib::string &configId,
                            const vespalib::string &docTypeName);
    ~DocumentDBConfigBuilder();

    DocumentDBConfigBuilder(const DocumentDBConfig &cfg);

    DocumentDBConfigBuilder &repo(const std::shared_ptr<const document::DocumentTypeRepo> &repo_in) {
        _repo = repo_in;
        return *this;
    }
    DocumentDBConfigBuilder &rankProfiles(const DocumentDBConfig::RankProfilesConfigSP &rankProfiles_in) {
        _rankProfiles = rankProfiles_in;
        return *this;
    }
    DocumentDBConfigBuilder &attributes(const DocumentDBConfig::AttributesConfigSP &attributes_in) {
        _attributes = attributes_in;
        return *this;
    }
    DocumentDBConfigBuilder &rankingConstants(const std::shared_ptr<const RankingConstants> &rankingConstants_in) {
        _rankingConstants = rankingConstants_in;
        return *this;
    }
    DocumentDBConfigBuilder &rankingExpressions(const std::shared_ptr<const RankingExpressions> &rankingExpressions_in) {
        _rankingExpressions = rankingExpressions_in;
        return *this;
    }
    DocumentDBConfigBuilder &onnxModels(const std::shared_ptr<const OnnxModels> &onnxModels_in) {
        _onnxModels = onnxModels_in;
        return *this;
    }
    DocumentDBConfigBuilder &importedFields(const DocumentDBConfig::ImportedFieldsConfigSP &importedFields_in) {
        _importedFields = importedFields_in;
        return *this;
    }
    DocumentDBConfigBuilder &summary(const DocumentDBConfig::SummaryConfigSP &summary_in) {
        _summary = summary_in;
        return *this;
    }
    DocumentDBConfig::SP build();
};

}
