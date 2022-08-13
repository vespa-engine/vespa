// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "config-verify-ranksetup.h"
#include <vespa/config-attributes.h>
#include <vespa/config-indexschema.h>
#include <vespa/config-rank-profiles.h>
#include <vespa/config/helper/legacy.h>
#include <vespa/config/common/configcontext.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_cache/constant_value.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/searchcommon/common/schemaconfigurer.h>
#include <vespa/searchcore/config/config-ranking-constants.h>
#include <vespa/searchcore/config/config-ranking-expressions.h>
#include <vespa/searchcore/config/config-onnx-models.h>
#include <vespa/searchcore/proton/matching/indexenvironment.h>
#include <vespa/searchcore/proton/matching/ranking_expressions.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/fef/test/plugin/setup.h>
#include <vespa/config/subscription/configsubscriber.hpp>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <optional>

#include <vespa/log/log.h>
LOG_SETUP(".verify-ranksetup");

using config::ConfigContext;
using config::ConfigHandle;
using config::ConfigRuntimeException;
using config::ConfigSubscriber;
using config::IConfigContext;
using config::InvalidConfigException;
using proton::matching::IConstantValueRepo;
using proton::matching::RankingExpressions;
using proton::matching::OnnxModels;
using vespa::config::search::AttributesConfig;
using vespa::config::search::IndexschemaConfig;
using vespa::config::search::RankProfilesConfig;
using vespa::config::search::core::RankingConstantsConfig;
using vespa::config::search::core::RankingExpressionsConfig;
using vespa::config::search::core::OnnxModelsConfig;
using vespa::config::search::core::VerifyRanksetupConfig;
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
make_expressions(const RankingExpressionsConfig &expressionsCfg, const VerifyRanksetupConfig &myCfg) {
    RankingExpressions expressions;
    for (const auto &entry: expressionsCfg.expression) {
        if (auto file = get_file(entry.fileref, myCfg)) {
            LOG(debug, "Add expression %s with ref=%s and path=%s", entry.name.c_str(), entry.fileref.c_str(), file.value().c_str());
            expressions.add(entry.name, file.value());
        } else {
            LOG(warning, "could not find file name for ranking expression '%s' (ref:'%s')",
                entry.name.c_str(), entry.fileref.c_str());
        }
    }
    return expressions;
}

OnnxModels
make_models(const OnnxModelsConfig &modelsCfg, const VerifyRanksetupConfig &myCfg) {
    OnnxModels::Vector model_list;
    for (const auto &entry: modelsCfg.model) {
        if (auto file = get_file(entry.fileref, myCfg)) {
            model_list.emplace_back(entry.name, file.value());
            OnnxModels::configure(entry, model_list.back());
        } else {
            LOG(warning, "could not find file name for onnx model '%s' (ref:'%s')",
                entry.name.c_str(), entry.fileref.c_str());
        }
    }
    return OnnxModels(model_list);
}

class VerifyRankSetup
{
private:
    std::vector<vespalib::string> _errors;
    bool verify(const search::index::Schema &schema,
                const search::fef::Properties &props,
                const IConstantValueRepo &repo,
                const RankingExpressions &expressions,
                const OnnxModels &models);

    bool verifyConfig(const VerifyRanksetupConfig &myCfg,
                      const RankProfilesConfig &rankCfg,
                      const IndexschemaConfig &schemaCfg,
                      const AttributesConfig &attributeCfg,
                      const RankingConstantsConfig &constantsCfg,
                      const RankingExpressionsConfig &expressionsCfg,
                      const OnnxModelsConfig &modelsCfg);

public:
    VerifyRankSetup();
    ~VerifyRankSetup();
    const std::vector<vespalib::string> & getMessages() const { return _errors; }
    bool verify(const std::string & configId);
};

struct DummyConstantValueRepo : IConstantValueRepo {
    const RankingConstantsConfig &cfg;
    DummyConstantValueRepo(const RankingConstantsConfig &cfg_in) : cfg(cfg_in) {}
    vespalib::eval::ConstantValue::UP getConstant(const vespalib::string &name) const override;
};

vespalib::eval::ConstantValue::UP
DummyConstantValueRepo::getConstant(const vespalib::string &name) const {
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
    return vespalib::eval::ConstantValue::UP(nullptr);
}

VerifyRankSetup::VerifyRankSetup()
    : _errors()
{ }

VerifyRankSetup::~VerifyRankSetup() = default;

bool
VerifyRankSetup::verify(const search::index::Schema &schema,
            const search::fef::Properties &props,
            const IConstantValueRepo &repo,
            const RankingExpressions &expressions,
            const OnnxModels &models)
{
    proton::matching::IndexEnvironment indexEnv(0, schema, props, repo, expressions, models);
    search::fef::BlueprintFactory factory;
    search::features::setup_search_features(factory);
    search::fef::test::setup_fef_test_plugin(factory);

    search::fef::RankSetup rankSetup(factory, indexEnv);
    rankSetup.configure(); // reads config values from the property map
    bool ok = true;
    if (!rankSetup.getFirstPhaseRank().empty()) {
        ok = verifyFeature(factory, indexEnv, rankSetup.getFirstPhaseRank(), "first phase ranking", _errors) && ok;
    }
    if (!rankSetup.getSecondPhaseRank().empty()) {
        ok = verifyFeature(factory, indexEnv, rankSetup.getSecondPhaseRank(), "second phase ranking", _errors) && ok;
    }
    for (size_t i = 0; i < rankSetup.getSummaryFeatures().size(); ++i) {
        ok = verifyFeature(factory, indexEnv, rankSetup.getSummaryFeatures()[i], "summary features", _errors) && ok;
    }
    for (const auto & feature : rankSetup.get_match_features()) {
        ok = verifyFeature(factory, indexEnv, feature, "match features", _errors) && ok;
    }
    for (size_t i = 0; i < rankSetup.getDumpFeatures().size(); ++i) {
        ok = verifyFeature(factory, indexEnv, rankSetup.getDumpFeatures()[i], "dump features", _errors) && ok;
    }
    return ok;
}

bool
VerifyRankSetup::verifyConfig(const VerifyRanksetupConfig &myCfg,
                  const RankProfilesConfig &rankCfg,
                  const IndexschemaConfig &schemaCfg,
                  const AttributesConfig &attributeCfg,
                  const RankingConstantsConfig &constantsCfg,
                  const RankingExpressionsConfig &expressionsCfg,
                  const OnnxModelsConfig &modelsCfg)
{
    bool ok = true;
    search::index::Schema schema;
    search::index::SchemaBuilder::build(schemaCfg, schema);
    search::index::SchemaBuilder::build(attributeCfg, schema);
    DummyConstantValueRepo repo(constantsCfg);
    auto expressions = make_expressions(expressionsCfg, myCfg);
    auto models = make_models(modelsCfg, myCfg);
    for(size_t i = 0; i < rankCfg.rankprofile.size(); i++) {
        search::fef::Properties properties;
        const RankProfilesConfig::Rankprofile &profile = rankCfg.rankprofile[i];
        for(size_t j = 0; j < profile.fef.property.size(); j++) {
            properties.add(profile.fef.property[j].name,
                           profile.fef.property[j].value);
        }
        vespalib::string msg;
        if (verify(schema, properties, repo, expressions, models)) {
            msg = fmt("rank profile '%s': pass", profile.name.c_str());
            LOG(info, "%s", msg.c_str());
        } else {
            LOG(error, "%s", msg.c_str());
            ok = false;
        }
        _errors.emplace_back(msg);
    }
    return ok;
}

bool
VerifyRankSetup::verify(const std::string & configid)
{
    LOG(debug, "verifying rank setup for config id '%s' ...",
        configid.c_str());

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

        subscriber.nextConfig();
        ok = verifyConfig(*myHandle->getConfig(),
                          *rankHandle->getConfig(),
                          *schemaHandle->getConfig(),
                          *attributesHandle->getConfig(),
                          *constantsHandle->getConfig(),
                          *expressionsHandle->getConfig(),
                          *modelsHandle->getConfig());
    } catch (ConfigRuntimeException & e) {
        vespalib::string msg = fmt("Unable to subscribe to config: %s", e.getMessage().c_str());
        LOG(error, "%s", msg.c_str());
        _errors.emplace_back(msg);
    } catch (InvalidConfigException & e) {
        vespalib::string msg = fmt("Error getting config: %s", e.getMessage().c_str());
        LOG(error, "%s", msg.c_str());
        _errors.emplace_back(msg);
    }
    return ok;
}

bool
verifyRankSetup(const char * configId, std::string & messages) {
    VerifyRankSetup verifier;
    bool ok = verifier.verify(configId);
    vespalib::asciistream os;
    for (const auto & m : verifier.getMessages()) {
        os << m << "\n";
    }
    messages = os.str();
    return ok;
}
