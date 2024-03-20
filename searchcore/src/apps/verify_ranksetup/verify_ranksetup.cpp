// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "verify_ranksetup.h"
#include "config-verify-ranksetup.h"
#include <vespa/config-attributes.h>
#include <vespa/config-indexschema.h>
#include <vespa/config-onnx-models.h>
#include <vespa/config-rank-profiles.h>
#include <vespa/config-ranking-constants.h>
#include <vespa/config-ranking-expressions.h>
#include <vespa/config/helper/legacy.h>
#include <vespa/config/common/configcontext.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_cache/constant_value.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/searchcommon/common/schemaconfigurer.h>
#include <vespa/searchcore/proton/matching/indexenvironment.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/fef/onnx_models.h>
#include <vespa/searchlib/fef/ranking_expressions.h>
#include <vespa/searchlib/fef/test/plugin/setup.h>
#include <vespa/searchvisitor/indexenvironment.h>
#include <vespa/searchvisitor/rankmanager.h>
#include <vespa/vsm/config/config-vsmfields.h>
#include <vespa/config/subscription/configsubscriber.hpp>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <optional>
#include <functional>

using config::ConfigContext;
using config::ConfigHandle;
using config::ConfigRuntimeException;
using config::ConfigSubscriber;
using config::IConfigContext;
using config::InvalidConfigException;
using search::fef::IRankingAssetsRepo;
using search::fef::OnnxModels;
using search::fef::RankingExpressions;
using vespa::config::search::AttributesConfig;
using vespa::config::search::IndexschemaConfig;
using vespa::config::search::RankProfilesConfig;
using vespa::config::search::core::RankingConstantsConfig;
using vespa::config::search::core::RankingExpressionsConfig;
using vespa::config::search::core::OnnxModelsConfig;
using vespa::config::search::core::VerifyRanksetupConfig;
using vespa::config::search::vsm::VsmfieldsConfig;
using vespalib::eval::BadConstantValue;
using vespalib::eval::ConstantValue;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::SimpleConstantValue;
using vespalib::eval::TensorSpec;
using vespalib::eval::Value;
using vespalib::eval::ValueType;
using vespalib::make_string_short::fmt;

std::optional<vespalib::string>
get_file(const vespalib::string &ref, const VerifyRanksetupConfig &myCfg) {
    for (const auto &entry: myCfg.file) {
        if (ref == entry.ref) {
            return entry.path;
        }
    }
    return std::nullopt;
}

RankingExpressions
make_expressions(const RankingExpressionsConfig &expressionsCfg, const VerifyRanksetupConfig &myCfg,
                 std::vector<search::fef::Message> & messages) {
    RankingExpressions expressions;
    for (const auto &entry: expressionsCfg.expression) {
        if (auto file = get_file(entry.fileref, myCfg)) {
            expressions.add(entry.name, file.value());
        } else {
            messages.emplace_back(search::fef::Level::WARNING,
                                  fmt("could not find file name for ranking expression '%s' (ref:'%s')",
                                      entry.name.c_str(), entry.fileref.c_str()));
        }
    }
    return expressions;
}

OnnxModels
make_models(const OnnxModelsConfig &modelsCfg, const VerifyRanksetupConfig &myCfg,
            std::vector<search::fef::Message> & messages) {
    OnnxModels::Vector model_list;
    for (const auto &entry: modelsCfg.model) {
        if (auto file = get_file(entry.fileref, myCfg)) {
            model_list.emplace_back(entry.name, file.value());
            OnnxModels::configure(entry, model_list.back());
        } else {
            messages.emplace_back(search::fef::Level::WARNING,
                                  fmt("could not find file name for onnx model '%s' (ref:'%s')",
                                      entry.name.c_str(), entry.fileref.c_str()));
        }
    }
    return OnnxModels(std::move(model_list));
}

class VerifyRankSetup
{
private:
    std::vector<search::fef::Message> _messages;
    SearchMode _searchMode;

    bool verifyIndexEnv(const search::fef::IIndexEnvironment &indexEnv);

    bool verifyConfig(const VerifyRanksetupConfig &myCfg,
                      const VsmfieldsConfig &vsmFieldsCcfg,
                      const RankProfilesConfig &rankCfg,
                      const IndexschemaConfig &schemaCfg,
                      const AttributesConfig &attributeCfg,
                      const RankingConstantsConfig &constantsCfg,
                      const RankingExpressionsConfig &expressionsCfg,
                      const OnnxModelsConfig &modelsCfg);

public:
    explicit VerifyRankSetup(SearchMode mode);
    ~VerifyRankSetup();
    [[nodiscard]] const std::vector<search::fef::Message> & getMessages() const { return _messages; }
    bool verify(const std::string & configId);
};

struct DummyRankingAssetsRepo : IRankingAssetsRepo {
    const RankingConstantsConfig &cfg;
    RankingExpressions _expressions;
    OnnxModels _onnxModels;
    DummyRankingAssetsRepo(const RankingConstantsConfig &cfg_in, RankingExpressions expressions, OnnxModels onnxModels);
    ~DummyRankingAssetsRepo() override;
    [[nodiscard]] vespalib::eval::ConstantValue::UP getConstant(const vespalib::string &name) const override;

    [[nodiscard]] vespalib::string getExpression(const vespalib::string & name) const override {
        return _expressions.loadExpression(name);
    }

    [[nodiscard]] const search::fef::OnnxModel *getOnnxModel(const vespalib::string & name) const override {
        return _onnxModels.getModel(name);
    }
};

DummyRankingAssetsRepo::DummyRankingAssetsRepo(const RankingConstantsConfig &cfg_in, RankingExpressions expressions, OnnxModels onnxModels)
    : cfg(cfg_in),
      _expressions(std::move(expressions)),
      _onnxModels(std::move(onnxModels))
{}

DummyRankingAssetsRepo::~DummyRankingAssetsRepo() = default;

vespalib::eval::ConstantValue::UP
DummyRankingAssetsRepo::getConstant(const vespalib::string &name) const {
    for (const auto &entry: cfg.constant) {
        if (entry.name == name) {
            try {
                auto tensor = vespalib::eval::value_from_spec(TensorSpec(entry.type), FastValueBuilderFactory::get());
                return std::make_unique<SimpleConstantValue>(std::move(tensor));
            } catch (std::exception &) {
                return std::make_unique<BadConstantValue>();
            }
        }
    }
    return {};
}

VerifyRankSetup::VerifyRankSetup(SearchMode mode)
    : _messages(),
      _searchMode(mode)
{ }

VerifyRankSetup::~VerifyRankSetup() = default;

bool
VerifyRankSetup::verifyIndexEnv(const search::fef::IIndexEnvironment &indexEnv) {
    search::fef::BlueprintFactory factory;
    search::features::setup_search_features(factory);
    search::fef::test::setup_fef_test_plugin(factory);

    search::fef::RankSetup rankSetup(factory, indexEnv);
    rankSetup.configure(); // reads config values from the property map
    bool ok = true;
    if (!rankSetup.getFirstPhaseRank().empty()) {
        ok = verifyFeature(factory, indexEnv, rankSetup.getFirstPhaseRank(), "first phase ranking", _messages) && ok;
    }
    if (!rankSetup.getSecondPhaseRank().empty()) {
        ok = verifyFeature(factory, indexEnv, rankSetup.getSecondPhaseRank(), "second phase ranking", _messages) && ok;
    }
    for (const auto & i : rankSetup.getSummaryFeatures()) {
        ok = verifyFeature(factory, indexEnv, i, "summary features", _messages) && ok;
    }
    for (const auto & feature : rankSetup.get_match_features()) {
        ok = verifyFeature(factory, indexEnv, feature, "match features", _messages) && ok;
    }
    for (const auto & i : rankSetup.getDumpFeatures()) {
        ok = verifyFeature(factory, indexEnv, i, "dump features", _messages) && ok;
    }
    return ok;
}

bool
VerifyRankSetup::verifyConfig(const VerifyRanksetupConfig &myCfg,
                              const VsmfieldsConfig &vsmFieldsCfg,
                              const RankProfilesConfig &rankCfg,
                              const IndexschemaConfig &schemaCfg,
                              const AttributesConfig &attributeCfg,
                              const RankingConstantsConfig &constantsCfg,
                              const RankingExpressionsConfig &expressionsCfg,
                              const OnnxModelsConfig &modelsCfg)
{
    bool ok = true;
    auto repo = std::make_shared<DummyRankingAssetsRepo>(constantsCfg,
                                                         make_expressions(expressionsCfg, myCfg, _messages),
                                                         make_models(modelsCfg, myCfg, _messages));

    using IndexEnvFactory = std::function<std::unique_ptr<search::fef::IIndexEnvironment>(const search::fef::Properties &)>;
    IndexEnvFactory factory;
    streaming::IndexEnvPrototype streamingProto;
    search::index::Schema schema;
    if (_searchMode == SearchMode::STREAMING) {
        streamingProto.set_ranking_assets_repo(repo);
        streamingProto.detectFields(vsmFieldsCfg);
        streamingProto.add_virtual_fields();
        factory = [&](const search::fef::Properties &properties)
                  {
                      auto indexEnv = streamingProto.clone();
                      indexEnv->getProperties().import(properties);
                      indexEnv->fixup_fields();
                      return indexEnv;
                  };
    } else {
        search::index::SchemaBuilder::build(schemaCfg, schema);
        search::index::SchemaBuilder::build(attributeCfg, schema);
        factory = [&](const search::fef::Properties &properties)
                  {
                      return std::make_unique<proton::matching::IndexEnvironment>(0, schema, properties, *repo);
                  };
    }
    for(const auto & profile : rankCfg.rankprofile) {
        search::fef::Properties properties;
        for(const auto & j : profile.fef.property) {
            properties.add(j.name, j.value);
        }
        auto indexEnvP = factory(properties);
        if (verifyIndexEnv(*indexEnvP)) {
            _messages.emplace_back(search::fef::Level::INFO,
                                   fmt("rank profile '%s': pass", profile.name.c_str()));
        } else {
            _messages.emplace_back(search::fef::Level::ERROR,
                                   fmt("rank profile '%s': FAIL", profile.name.c_str()));
            ok = false;
        }
    }
    return ok;
}

bool
VerifyRankSetup::verify(const std::string & configid)
{
    bool ok = false;
    try {
        auto ctx = std::make_shared<ConfigContext>(*config::legacyConfigId2Spec(configid));
        vespalib::string cfgId(config::legacyConfigId2ConfigId(configid));
        ConfigSubscriber subscriber(ctx);
        ConfigHandle<VerifyRanksetupConfig>::UP myHandle = subscriber.subscribe<VerifyRanksetupConfig>(cfgId);
        ConfigHandle<RankProfilesConfig>::UP rankHandle = subscriber.subscribe<RankProfilesConfig>(cfgId);
        ConfigHandle<AttributesConfig>::UP attributesHandle = subscriber.subscribe<AttributesConfig>(cfgId);
        ConfigHandle<IndexschemaConfig>::UP schemaHandle = subscriber.subscribe<IndexschemaConfig>(cfgId);
        ConfigHandle<RankingConstantsConfig>::UP constantsHandle = subscriber.subscribe<RankingConstantsConfig>(cfgId);
        ConfigHandle<RankingExpressionsConfig>::UP expressionsHandle = subscriber.subscribe<RankingExpressionsConfig>(cfgId);
        ConfigHandle<OnnxModelsConfig>::UP modelsHandle = subscriber.subscribe<OnnxModelsConfig>(cfgId);

        std::unique_ptr<VsmfieldsConfig> vsmFieldsCfg = std::make_unique<VsmfieldsConfig>();
        ConfigHandle<VsmfieldsConfig>::UP vsmFieldsHandle;
        if (_searchMode == SearchMode::STREAMING) {
            vsmFieldsHandle = subscriber.subscribe<VsmfieldsConfig>(cfgId);
        }
        subscriber.nextConfig();
        if (_searchMode == SearchMode::STREAMING) {
            vsmFieldsCfg = vsmFieldsHandle->getConfig();
        }
        ok = verifyConfig(*myHandle->getConfig(),
                          *vsmFieldsCfg,
                          *rankHandle->getConfig(),
                          *schemaHandle->getConfig(),
                          *attributesHandle->getConfig(),
                          *constantsHandle->getConfig(),
                          *expressionsHandle->getConfig(),
                          *modelsHandle->getConfig());
    } catch (ConfigRuntimeException & e) {
        _messages.emplace_back(search::fef::Level::ERROR,
                               fmt("Unable to subscribe to config: %s", e.getMessage().c_str()));
    } catch (InvalidConfigException & e) {
        _messages.emplace_back(search::fef::Level::ERROR,
                               fmt("Error getting config: %s", e.getMessage().c_str()));
    }
    return ok;
}

std::pair<bool, std::vector<search::fef::Message>>
verifyRankSetup(const char * configId, SearchMode mode) {
    VerifyRankSetup verifier{mode};
    bool ok = verifier.verify(configId);

    return {ok, verifier.getMessages()};
}
