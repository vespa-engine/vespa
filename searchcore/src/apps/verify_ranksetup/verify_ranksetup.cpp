// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "config-verify-ranksetup.h"
#include <vespa/config-attributes.h>
#include <vespa/config-imported-fields.h>
#include <vespa/config-indexschema.h>
#include <vespa/config-rank-profiles.h>
#include <vespa/config/config.h>
#include <vespa/config/helper/legacy.h>
#include <vespa/config/common/configcontext.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_cache/constant_value.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/searchcommon/common/schemaconfigurer.h>
#include <vespa/searchcore/config/config-ranking-constants.h>
#include <vespa/searchcore/config/config-onnx-models.h>
#include <vespa/searchcore/proton/matching/indexenvironment.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/fef/test/plugin/setup.h>
#include <vespa/fastos/app.h>
#include <optional>

#include <vespa/log/log.h>
LOG_SETUP("vespa-verify-ranksetup");

using config::ConfigContext;
using config::ConfigHandle;
using config::ConfigRuntimeException;
using config::ConfigSubscriber;
using config::IConfigContext;
using config::InvalidConfigException;
using proton::matching::IConstantValueRepo;
using proton::matching::OnnxModels;
using vespa::config::search::AttributesConfig;
using vespa::config::search::IndexschemaConfig;
using vespa::config::search::RankProfilesConfig;
using vespa::config::search::core::RankingConstantsConfig;
using vespa::config::search::core::OnnxModelsConfig;
using vespa::config::search::core::VerifyRanksetupConfig;
using vespalib::eval::BadConstantValue;
using vespalib::eval::ConstantValue;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::SimpleConstantValue;
using vespalib::eval::TensorSpec;
using vespalib::eval::Value;
using vespalib::eval::ValueType;

std::optional<vespalib::string> get_file(const vespalib::string &ref, const VerifyRanksetupConfig &myCfg) {
    for (const auto &entry: myCfg.file) {
        if (ref == entry.ref) {
            return entry.path;
        }
    }
    return std::nullopt;
}

OnnxModels make_models(const OnnxModelsConfig &modelsCfg, const VerifyRanksetupConfig &myCfg) {
    OnnxModels::Vector model_list;
    for (const auto &entry: modelsCfg.model) {
        if (auto file = get_file(entry.fileref, myCfg)) {
            model_list.emplace_back(entry.name, file.value());
            OnnxModels::configure(entry, model_list.back());
        } else {
            LOG(warning, "could not find file for onnx model '%s' (ref:'%s')\n",
                entry.name.c_str(), entry.fileref.c_str());
        }
    }
    return OnnxModels(model_list);
}

class App : public FastOS_Application
{
public:
    bool verify(const search::index::Schema &schema,
                const search::fef::Properties &props,
                const IConstantValueRepo &repo,
                OnnxModels models);

    bool verifyConfig(const VerifyRanksetupConfig &myCfg,
                      const RankProfilesConfig &rankCfg,
                      const IndexschemaConfig &schemaCfg,
                      const AttributesConfig &attributeCfg,
                      const RankingConstantsConfig &constantsCfg,
                      const OnnxModelsConfig &modelsCfg);

    int usage();
    int Main() override;
};

struct DummyConstantValueRepo : IConstantValueRepo {
    const RankingConstantsConfig &cfg;
    DummyConstantValueRepo(const RankingConstantsConfig &cfg_in) : cfg(cfg_in) {}
    virtual vespalib::eval::ConstantValue::UP getConstant(const vespalib::string &name) const override {
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
};

bool
App::verify(const search::index::Schema &schema,
            const search::fef::Properties &props,
            const IConstantValueRepo &repo,
            OnnxModels models)
{
    proton::matching::IndexEnvironment indexEnv(0, schema, props, repo, models);
    search::fef::BlueprintFactory factory;
    search::features::setup_search_features(factory);
    search::fef::test::setup_fef_test_plugin(factory);

    search::fef::RankSetup rankSetup(factory, indexEnv);
    rankSetup.configure(); // reads config values from the property map
    bool ok = true;
    if (!rankSetup.getFirstPhaseRank().empty()) {
        ok = verifyFeature(factory, indexEnv, rankSetup.getFirstPhaseRank(), "first phase ranking") && ok;
    }
    if (!rankSetup.getSecondPhaseRank().empty()) {
        ok = verifyFeature(factory, indexEnv, rankSetup.getSecondPhaseRank(), "second phase ranking") && ok;
    }
    for (size_t i = 0; i < rankSetup.getSummaryFeatures().size(); ++i) {
        ok = verifyFeature(factory, indexEnv, rankSetup.getSummaryFeatures()[i], "summary features") && ok;
    }
    for (size_t i = 0; i < rankSetup.getDumpFeatures().size(); ++i) {
        ok = verifyFeature(factory, indexEnv, rankSetup.getDumpFeatures()[i], "dump features") && ok;
    }
    return ok;
}

bool
App::verifyConfig(const VerifyRanksetupConfig &myCfg,
                  const RankProfilesConfig &rankCfg,
                  const IndexschemaConfig &schemaCfg,
                  const AttributesConfig &attributeCfg,
                  const RankingConstantsConfig &constantsCfg,
                  const OnnxModelsConfig &modelsCfg)
{
    bool ok = true;
    search::index::Schema schema;
    search::index::SchemaBuilder::build(schemaCfg, schema);
    search::index::SchemaBuilder::build(attributeCfg, schema);
    DummyConstantValueRepo repo(constantsCfg);
    auto models = make_models(modelsCfg, myCfg);
    for(size_t i = 0; i < rankCfg.rankprofile.size(); i++) {
        search::fef::Properties properties;
        const RankProfilesConfig::Rankprofile &profile = rankCfg.rankprofile[i];
        for(size_t j = 0; j < profile.fef.property.size(); j++) {
            properties.add(profile.fef.property[j].name,
                           profile.fef.property[j].value);
        }
        if (verify(schema, properties, repo, models)) {
            LOG(info, "rank profile '%s': pass", profile.name.c_str());
        } else {
            LOG(error, "rank profile '%s': FAIL", profile.name.c_str());
            ok = false;
        }
    }
    return ok;
}

int
App::usage()
{
    fprintf(stderr, "Usage: vespa-verify-ranksetup <config-id>\n");
    return 1;
}

int
App::Main()
{
    if (_argc != 2) {
        return usage();
    }

    const std::string configid = _argv[1];
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
        ConfigHandle<OnnxModelsConfig>::UP modelsHandle = subscriber.subscribe<OnnxModelsConfig>(cfgId);

        subscriber.nextConfig();
        ok = verifyConfig(*myHandle->getConfig(),
                          *rankHandle->getConfig(),
                          *schemaHandle->getConfig(),
                          *attributesHandle->getConfig(),
                          *constantsHandle->getConfig(),
                          *modelsHandle->getConfig());
    } catch (ConfigRuntimeException & e) {
        LOG(error, "Unable to subscribe to config: %s", e.getMessage().c_str());
    } catch (InvalidConfigException & e) {
        LOG(error, "Error getting config: %s", e.getMessage().c_str());
    }
    if (!ok) {
        return 1;
    }
    return 0;
}

int main(int argc, char **argv) {
    App app;
    return app.Entry(argc, argv);
}
