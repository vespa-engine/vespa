// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_db_config_set_builder.h"

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

} // namespace proton::test
