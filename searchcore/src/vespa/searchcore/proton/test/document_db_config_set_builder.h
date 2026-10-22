// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/config-attributes.h>
#include <vespa/config-imported-fields.h>
#include <vespa/config-indexschema.h>
#include <vespa/config-onnx-models.h>
#include <vespa/config-rank-profiles.h>
#include <vespa/config-ranking-constants.h>
#include <vespa/config-ranking-expressions.h>
#include <vespa/config-summary.h>
#include <vespa/config/subscription/sourcespec.h>
#include <vespa/searchsummary/config/config-juniperrc.h>

namespace proton::test {

/*
 * Builders for the portion of a config set related to a specific document db.
 */
struct DocumentDBConfigSetBuilder {
    using AttributesConfigBuilder = vespa::config::search::AttributesConfigBuilder;
    using ImportedFieldsConfigBuilder = vespa::config::search::ImportedFieldsConfigBuilder;
    using IndexschemaConfigBuilder = vespa::config::search::IndexschemaConfigBuilder;
    using JuniperrcConfigBuilder = vespa::config::search::summary::JuniperrcConfigBuilder;
    using OnnxModelsConfigBuilder = vespa::config::search::core::OnnxModelsConfigBuilder;
    using RankProfilesConfigBuilder = vespa::config::search::RankProfilesConfigBuilder;
    using RankingConstantsConfigBuilder = vespa::config::search::core::RankingConstantsConfigBuilder;
    using RankingExpressionsConfigBuilder = vespa::config::search::core::RankingExpressionsConfigBuilder;
    using SummaryConfigBuilder = vespa::config::search::SummaryConfigBuilder;

    AttributesConfigBuilder         attributes;
    ImportedFieldsConfigBuilder     imported_fields;
    IndexschemaConfigBuilder        indexschema;
    JuniperrcConfigBuilder          juniperrc;
    OnnxModelsConfigBuilder         onnx_models;
    RankProfilesConfigBuilder       rank_profiles;
    RankingConstantsConfigBuilder   ranking_constants;
    RankingExpressionsConfigBuilder ranking_expressions;
    SummaryConfigBuilder            summary;

    DocumentDBConfigSetBuilder();
    ~DocumentDBConfigSetBuilder();

    void add_builders(config::ConfigSet& set, const std::string& config_id);
};

} // namespace proton::test
