// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("verify_ranksetup");
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchcommon/common/schemaconfigurer.h>
#include <vespa/searchcore/proton/matching/indexenvironment.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/test/plugin/setup.h>
#include <vespa/config/config.h>
#include <vespa/config/helper/legacy.h>

#include <vespa/config-rank-profiles.h>
#include <vespa/config-indexschema.h>
#include <vespa/config-attributes.h>

using config::IConfigContext;
using config::ConfigContext;
using config::ConfigSubscriber;
using config::ConfigHandle;
using vespa::config::search::RankProfilesConfig;
using vespa::config::search::IndexschemaConfig;
using vespa::config::search::AttributesConfig;
using config::ConfigRuntimeException;
using config::InvalidConfigException;

class App : public FastOS_Application
{
public:
    bool verify(const search::index::Schema &schema,
                const search::fef::Properties &props);

    bool verifyConfig(const vespa::config::search::RankProfilesConfig &rankCfg,
                      const vespa::config::search::IndexschemaConfig  &schemaCfg,
                      const vespa::config::search::AttributesConfig   &attributeCfg);

    int usage();
    int Main();
};

bool
App::verify(const search::index::Schema &schema,
            const search::fef::Properties &props)
{
    proton::matching::IndexEnvironment indexEnv(schema, props);
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
App::verifyConfig(const vespa::config::search::RankProfilesConfig &rankCfg,
                  const vespa::config::search::IndexschemaConfig  &schemaCfg,
                  const vespa::config::search::AttributesConfig   &attributeCfg)
{
    bool ok = true;
    search::index::Schema schema;
    search::index::SchemaBuilder::build(schemaCfg, schema);
    search::index::SchemaBuilder::build(attributeCfg, schema);

    for(size_t i = 0; i < rankCfg.rankprofile.size(); i++) {
        search::fef::Properties properties;
        const vespa::config::search::RankProfilesConfig::Rankprofile &profile = rankCfg.rankprofile[i];
        for(size_t j = 0; j < profile.fef.property.size(); j++) {
            properties.add(profile.fef.property[j].name,
                           profile.fef.property[j].value);
        }
        if (verify(schema, properties)) {
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
    fprintf(stderr, "Usage: verify_ranksetup <config-id>\n");
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
        IConfigContext::SP ctx(new ConfigContext(*config::legacyConfigId2Spec(configid)));
        vespalib::string cfgId(config::legacyConfigId2ConfigId(configid));
        ConfigSubscriber subscriber(ctx);
        ConfigHandle<RankProfilesConfig>::UP rankHandle = subscriber.subscribe<RankProfilesConfig>(cfgId);
        ConfigHandle<AttributesConfig>::UP attributesHandle = subscriber.subscribe<AttributesConfig>(cfgId);
        ConfigHandle<IndexschemaConfig>::UP schemaHandle = subscriber.subscribe<IndexschemaConfig>(cfgId);

        subscriber.nextConfig();
        ok = verifyConfig(*rankHandle->getConfig(), *schemaHandle->getConfig(), *attributesHandle->getConfig());
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
