// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bootstrap_config_set_builder.h"

#include <vespa/document/repo/document_type_repo_factory.h>
#include <vespa/searchcore/proton/server/bootstrapconfig.h>

using document::DocumentTypeRepo;
using document::DocumentTypeRepoFactory;
using search::TuneFileDocumentDB;
using vespalib::HwInfo;

namespace proton::test {

BootstrapConfigSetBuilder::BootstrapConfigSetBuilder()
    : bucketspaces(), documenttypes(), filedistributorrpc(), proton(), telemetry() {
}

BootstrapConfigSetBuilder::~BootstrapConfigSetBuilder() = default;

void BootstrapConfigSetBuilder::add_builders(config::ConfigSet& set, const std::string& config_id) {
    set.addBuilder(config_id, &bucketspaces);
    set.addBuilder(config_id, &documenttypes);
    set.addBuilder(config_id, &filedistributorrpc);
    set.addBuilder(config_id, &proton);
    set.addBuilder(config_id, &telemetry);
}

std::shared_ptr<BootstrapConfig> BootstrapConfigSetBuilder::build(int64_t                                 generation,
                                                                  std::shared_ptr<const DocumentTypeRepo> repo,
                                                                  const HwInfo& hwInfo) const {
    return std::make_shared<BootstrapConfig>(
        generation, std::make_shared<DocumenttypesConfig>(documenttypes), std::move(repo),
        std::make_shared<ProtonConfig>(proton), std::make_shared<FiledistributorrpcConfig>(filedistributorrpc),
        std::make_shared<BucketspacesConfig>(bucketspaces), std::make_shared<TelemetryConfig>(telemetry),
        std::make_shared<TuneFileDocumentDB>(), hwInfo);
}

std::shared_ptr<BootstrapConfig> BootstrapConfigSetBuilder::build(int64_t generation, const HwInfo& hwInfo) const {
    return build(generation, DocumentTypeRepoFactory::make(documenttypes), hwInfo);
}

} // namespace proton::test
