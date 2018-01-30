// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/server/documentdbconfig.h>

namespace proton::test {

/**
 * Builder for instances of DocumentDBConfig used in unit tests.
 */
class DocumentDBConfigBuilder {
private:
    int64_t _generation;
    DocumentDBConfig::RankProfilesConfigSP _rankProfiles;
    DocumentDBConfig::RankingConstants::SP _rankingConstants;
    DocumentDBConfig::IndexschemaConfigSP _indexschema;
    DocumentDBConfig::AttributesConfigSP _attributes;
    DocumentDBConfig::SummaryConfigSP _summary;
    DocumentDBConfig::SummarymapConfigSP _summarymap;
    DocumentDBConfig::JuniperrcConfigSP _juniperrc;
    DocumentDBConfig::DocumenttypesConfigSP _documenttypes;
    document::DocumentTypeRepo::SP _repo;
    DocumentDBConfig::ImportedFieldsConfigSP _importedFields;
    search::TuneFileDocumentDB::SP _tuneFileDocumentDB;
    search::index::Schema::SP _schema;
    DocumentDBConfig::MaintenanceConfigSP _maintenance;
    search::LogDocumentStore::Config _store;
    vespalib::string _configId;
    vespalib::string _docTypeName;

public:
    DocumentDBConfigBuilder(int64_t generation,
                            const search::index::Schema::SP &schema,
                            const vespalib::string &configId,
                            const vespalib::string &docTypeName);
    ~DocumentDBConfigBuilder();

    DocumentDBConfigBuilder(const DocumentDBConfig &cfg);

    DocumentDBConfigBuilder &repo(const document::DocumentTypeRepo::SP &repo_in) {
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
    DocumentDBConfigBuilder &rankingConstants(const DocumentDBConfig::RankingConstants::SP &rankingConstants_in) {
        _rankingConstants = rankingConstants_in;
        return *this;
    }
    DocumentDBConfigBuilder &importedFields(const DocumentDBConfig::ImportedFieldsConfigSP &importedFields_in) {
        _importedFields = importedFields_in;
        return *this;
    }
    DocumentDBConfigBuilder &summarymap(const DocumentDBConfig::SummarymapConfigSP &summarymap_in) {
        _summarymap = summarymap_in;
        return *this;
    }
    DocumentDBConfig::SP build();
};

}
