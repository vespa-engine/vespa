// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "featuretest.h"
#include <vespa/searchlib/fef/utils.h>
#include <vespa/vespalib/testkit/test_kit.h>

#include <vespa/log/log.h>
LOG_SETUP(".fef.featuretest");

namespace search::fef::test {

FeatureTest::FeatureTest(BlueprintFactory &factory,
                         const IndexEnvironment &indexEnv,
                         QueryEnvironment &queryEnv,
                         MatchDataLayout &layout,
                         const std::vector<vespalib::string> &features,
                         const Properties &overrides) :
    _factory(factory),
    _indexEnv(indexEnv),
    _queryEnv(queryEnv),
    _features(features),
    _layout(layout),
    _overrides(overrides),
    _resolver(new BlueprintResolver(factory, indexEnv)),
    _match_data(_layout.createMatchData()),
    _rankProgram(new RankProgram(_resolver)),
    _doneSetup(false)
{
}

FeatureTest::~FeatureTest() {}

FeatureTest::FeatureTest(BlueprintFactory &factory,
                         const IndexEnvironment &indexEnv,
                         QueryEnvironment &queryEnv,
                         MatchDataLayout &layout,
                         const vespalib::string &feature,
                         const Properties &overrides) :
    _factory(factory),
    _indexEnv(indexEnv),
    _queryEnv(queryEnv),
    _features(),
    _layout(layout),
    _overrides(overrides),
    _resolver(new BlueprintResolver(factory, indexEnv)),
    _match_data(_layout.createMatchData()),
    _rankProgram(new RankProgram(_resolver)),
    _doneSetup(false)
{
    _features.push_back(feature);
}

bool
FeatureTest::setup()
{
    if (_doneSetup) {
        LOG(error, "Setup already done.");
        return false;
    }

    // clear state so that setup can be called multiple times.
    clear();

    for (uint32_t i = 0; i < _features.size(); ++i) {
        _resolver->addSeed(_features[i]);
    }

    if (!_resolver->compile()) {
        LOG(error, "Failed to compile blueprint resolver.");
        return false;
    }
    for (const auto &spec: _resolver->getExecutorSpecs()) {
        spec.blueprint->prepareSharedState(_queryEnv, _queryEnv.getObjectStore());
    }
    _rankProgram->setup(*_match_data, _queryEnv, _overrides);
    _doneSetup = true;
    return true;
}

MatchDataBuilder::UP
FeatureTest::createMatchDataBuilder()
{
    if (_doneSetup) {
        return MatchDataBuilder::UP(new MatchDataBuilder(_queryEnv, *_match_data));
    }
    LOG(warning, "Match data not initialized.");
    return MatchDataBuilder::UP();
}

bool
FeatureTest::execute(const RankResult &expected, uint32_t docId)
{
    RankResult result;
    if (!executeOnly(result, docId)) {
        return false;
    }

    if (!result.includes(expected)) {
        std::stringstream exp, act;
        exp << "Expected: " << expected;
        act << "Actual  : " << result;

        LOG(error, "Expected result not present in actual result after execution:");
        LOG(error, "%s", exp.str().c_str());
        LOG(error, "%s", act.str().c_str());

        return false;
    }
    return true;
}

bool
FeatureTest::execute(feature_t expected, double epsilon, uint32_t docId)
{
    return execute(RankResult().setEpsilon(epsilon).addScore(_features.front(), expected), docId);
}

bool
FeatureTest::executeOnly(RankResult & result, uint32_t docId)
{
    if (!_doneSetup) {
        LOG(error, "Setup not done.");
        return false;
    }
    std::map<vespalib::string, feature_t> all = Utils::getAllFeatures(*_rankProgram, docId);
    for (auto itr = all.begin(); itr != all.end(); ++itr) {
        result.addScore(itr->first, itr->second);
    }
    return true;
}

vespalib::eval::Value::CREF
FeatureTest::resolveObjectFeature(uint32_t docid)
{
    return Utils::getObjectFeature(*_rankProgram, docid);
}

void
FeatureTest::clear()
{
    _resolver = BlueprintResolver::SP(new BlueprintResolver(_factory, _indexEnv));
    _match_data = _layout.createMatchData();
    _rankProgram.reset(new RankProgram(_resolver));
    _doneSetup = false;
}

}
