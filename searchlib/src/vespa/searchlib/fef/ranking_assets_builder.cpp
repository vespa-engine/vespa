// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "ranking_assets_builder.h"
#include "onnx_models.h"
#include "ranking_constants.h"
#include "ranking_expressions.h"
#include <vespa/config/common/exceptions.h>
#include <vespa/config/file_acquirer/file_acquirer.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/time.h>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP(".fef.ranking_assets_builder");


using vespa::config::search::core::OnnxModelsConfig;
using vespa::config::search::core::RankingConstantsConfig;
using vespa::config::search::core::RankingExpressionsConfig;
using vespalib::make_string_short::fmt;

namespace search::fef {

constexpr vespalib::duration file_resolve_timeout = 60min;

RankingAssetsBuilder::RankingAssetsBuilder(FNET_Transport* transport, const vespalib::string& file_distributor_connection_spec)
    : _file_acquirer(),
      _time_box(vespalib::to_s(file_resolve_timeout), 5)
{
    if (transport != nullptr && file_distributor_connection_spec != "") {
        _file_acquirer = std::make_unique<config::RpcFileAcquirer>(*transport, file_distributor_connection_spec);
    }
}

RankingAssetsBuilder::~RankingAssetsBuilder() = default;

vespalib::string
RankingAssetsBuilder::resolve_file(const vespalib::string& desc, const vespalib::string& fileref)
{
    vespalib::string file_path;
    LOG(debug, "Waiting for file acquirer (%s, ref='%s')", desc.c_str(), fileref.c_str());
    while (_time_box.hasTimeLeft() && (file_path == "")) {
        file_path = _file_acquirer->wait_for(fileref, _time_box.timeLeft());
        if (file_path == "") {
            std::this_thread::sleep_for(100ms);
        }
    }
    LOG(debug, "Got file path from file acquirer: '%s' (%s, ref='%s')", file_path.c_str(), desc.c_str(), fileref.c_str());
    if (file_path == "") {
        throw config::ConfigTimeoutException(fmt("could not get file path from file acquirer for %s (ref=%s)",
                                                 desc.c_str(), fileref.c_str()));
    }
    return file_path;
}

std::shared_ptr<const OnnxModels>
RankingAssetsBuilder::build(const OnnxModelsConfig& config)
{
    OnnxModels::Vector models;
    if (_file_acquirer) {
        for (const auto& rc : config.model) {
            auto desc = fmt("name='%s'", rc.name.c_str());
            vespalib::string file_path = resolve_file(desc, rc.fileref);
            models.emplace_back(rc.name, file_path);
            OnnxModels::configure(rc, models.back());
        }
    }
    return std::make_shared<OnnxModels>(std::move(models));
}

std::shared_ptr<const RankingConstants>
RankingAssetsBuilder::build(const RankingConstantsConfig& config)
{
    RankingConstants::Vector constants;
    if (_file_acquirer) {
        for (const auto& rc : config.constant) {
            auto desc = fmt("name='%s', type='%s'", rc.name.c_str(), rc.type.c_str());
            vespalib::string file_path = resolve_file(desc, rc.fileref);
            constants.emplace_back(rc.name, rc.type, file_path);
        }
    }
    return std::make_shared<RankingConstants>(constants);
}

std::shared_ptr<const RankingExpressions>
RankingAssetsBuilder::build(const RankingExpressionsConfig& config)
{
    RankingExpressions expressions;
    if (_file_acquirer) {
        for (const auto& rc : config.expression) {
            auto desc = fmt("name='%s'", rc.name.c_str());
            vespalib::string filePath = resolve_file(desc, rc.fileref);
            expressions.add(rc.name, filePath);
        }
    }
    return std::make_shared<RankingExpressions>(std::move(expressions));
}

}
