// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/config-onnx-models.h>
#include <vespa/config-ranking-constants.h>
#include <vespa/config-ranking-expressions.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/time/time_box.h>

class FNET_Transport;

namespace config { struct FileAcquirer; }

namespace search::fef {

class OnnxModels;
class RankingConstants;
class RankingExpressions;

/*
 * Builder class for ranking assets (OnnxModels, RankingConstants, RankingExpressions).
 */
class RankingAssetsBuilder {
    std::unique_ptr<config::FileAcquirer> _file_acquirer;
    vespalib::TimeBox                     _time_box;

    vespalib::string resolve_file(const vespalib::string& desc, const vespalib::string& fileref);
public:
    RankingAssetsBuilder(FNET_Transport* transport, const vespalib::string& file_distributor_connection_spec);
    ~RankingAssetsBuilder();
    std::shared_ptr<const OnnxModels> build(const vespa::config::search::core::OnnxModelsConfig& config);
    std::shared_ptr<const RankingConstants> build(const vespa::config::search::core::RankingConstantsConfig& config);
    std::shared_ptr<const RankingExpressions> build(const vespa::config::search::core::RankingExpressionsConfig& config);
};

}
