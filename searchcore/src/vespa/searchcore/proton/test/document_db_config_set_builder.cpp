// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_db_config_set_builder.h"

#include <vespa/document/repo/document_type_repo_factory.h>

using document::DocumentTypeRepo;
using document::DocumentTypeRepoFactory;
using search::TuneFileDocumentDB;

namespace proton::test {

DocumentDBConfigSetBuilder::DocumentDBConfigSetBuilder()
    : attributes(),
      imported_fields(),
      indexschema(),
      juniperrc(),
      onnx_models(),
      rank_profiles(),
      ranking_constants(),
      ranking_expressions(),
      summary() {
}

DocumentDBConfigSetBuilder::~DocumentDBConfigSetBuilder() = default;

void DocumentDBConfigSetBuilder::add_builders(config::ConfigSet& set, const std::string& config_id) {
    set.addBuilder(config_id, &attributes);
    set.addBuilder(config_id, &imported_fields);
    set.addBuilder(config_id, &indexschema);
    set.addBuilder(config_id, &juniperrc);
    set.addBuilder(config_id, &onnx_models);
    set.addBuilder(config_id, &rank_profiles);
    set.addBuilder(config_id, &ranking_constants);
    set.addBuilder(config_id, &ranking_expressions);
    set.addBuilder(config_id, &summary);
}

std::shared_ptr<DocumentDBConfig> DocumentDBConfigSetBuilder::build(int64_t                    generation,
                                                                    const DocumenttypesConfig& documenttypes,
                                                                    std::shared_ptr<const DocumentTypeRepo> repo,
                                                                    const std::string& doc_type_name,
                                                                    const std::string& config_id) const {
    auto schema = DocumentDBConfig::build_schema(attributes, indexschema);
    // Not using ranking assets, thus empty ranking constants, ranking expressions and onnx models
    return std::make_shared<DocumentDBConfig>(
        generation, std::make_shared<RankProfilesConfig>(rank_profiles),
        std::make_shared<search::fef::RankingConstants>(), std::make_shared<search::fef::RankingExpressions>(),
        std::make_shared<search::fef::OnnxModels>(), std::make_shared<IndexschemaConfig>(indexschema),
        std::make_shared<AttributesConfig>(attributes), std::make_shared<SummaryConfig>(summary),
        std::make_shared<JuniperrcConfig>(juniperrc), std::make_shared<DocumenttypesConfig>(documenttypes),
        std::move(repo), std::make_shared<ImportedFieldsConfig>(imported_fields),
        std::make_shared<TuneFileDocumentDB>(), schema, std::make_shared<DocumentDBMaintenanceConfig>(),
        search::LogDocumentStore::Config(), ThreadingServiceConfig::make(), AllocConfig::makeDefault(),
        DocumentMetaStoreConfig::make(), config_id, doc_type_name);
}

std::shared_ptr<DocumentDBConfig> DocumentDBConfigSetBuilder::build(int64_t                    generation,
                                                                    const DocumenttypesConfig& documenttypes,
                                                                    const std::string&         doc_type_name,
                                                                    const std::string&         config_id) const {
    return build(generation, documenttypes, DocumentTypeRepoFactory::make(documenttypes), doc_type_name, config_id);
}

} // namespace proton::test
