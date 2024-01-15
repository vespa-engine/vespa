// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "ftlib.h"
#include <vespa/searchlib/features/utils.h>
#include <vespa/vespalib/text/stringtokenizer.h>

using namespace search::features;
using namespace search::fef;
using namespace search::fef::test;

FtIndexEnvironment::FtIndexEnvironment() :
    search::fef::test::IndexEnvironment(),
    _builder(*this)
{
}

FtQueryEnvironment::FtQueryEnvironment(search::fef::test::IndexEnvironment &env)
    : search::fef::test::QueryEnvironment(&env),
      _layout(),
      _builder(*this, _layout)
{
}

FtQueryEnvironment::~FtQueryEnvironment() = default;

FtDumpFeatureVisitor::FtDumpFeatureVisitor() = default;

FtFeatureTest::FtFeatureTest(search::fef::BlueprintFactory &factory, const vespalib::string &feature) :
    _indexEnv(),
    _queryEnv(_indexEnv),
    _overrides(),
    _test(factory, _indexEnv, _queryEnv, _queryEnv.getLayout(), feature, _overrides)
{
}

FtFeatureTest::FtFeatureTest(search::fef::BlueprintFactory &factory, const std::vector<vespalib::string> &features)
    : _indexEnv(),
      _queryEnv(_indexEnv),
      _overrides(),
      _test(factory, _indexEnv, _queryEnv, _queryEnv.getLayout(), features, _overrides)
{
}

FtFeatureTest::~FtFeatureTest() = default;

//---------------------------------------------------------------------------------------------------------------------
// FtUtil
//---------------------------------------------------------------------------------------------------------------------
std::vector<vespalib::string>
FtUtil::tokenize(const vespalib::string & str, const vespalib::string & separator)
{
    std::vector<vespalib::string> retval;
    if (separator != vespalib::string("")) {
        vespalib::StringTokenizer tnz(str, separator);
        tnz.removeEmptyTokens();
        for (auto token : tnz) {
            retval.emplace_back(token);
        }
    } else {
        for (uint32_t i = 0; i < str.size(); ++i) {
            retval.emplace_back(str.substr(i, 1));
        }
    }
    return retval;
}


FtQuery
FtUtil::toQuery(const vespalib::string & query, const vespalib::string & separator)
{
    std::vector<vespalib::string> prepQuery = FtUtil::tokenize(query, separator);
    FtQuery retval(prepQuery.size());
    for (uint32_t i = 0; i < prepQuery.size(); ++i) {
        std::vector<vespalib::string> significanceSplit = FtUtil::tokenize(prepQuery[i], vespalib::string("%"));
        std::vector<vespalib::string> weightSplit = FtUtil::tokenize(significanceSplit[0], vespalib::string("!"));
        std::vector<vespalib::string> connexitySplit = FtUtil::tokenize(weightSplit[0], vespalib::string(":"));
        if (connexitySplit.size() > 1) {
            retval[i].term = connexitySplit[1];
            retval[i].connexity = util::strToNum<feature_t>(connexitySplit[0]);
        } else {
            retval[i].term = connexitySplit[0];
        }
        if (significanceSplit.size() > 1) {
            retval[i].significance = util::strToNum<feature_t>(significanceSplit[1]);
        }
        if (weightSplit.size() > 1) {
            retval[i].termWeight.setPercent(util::strToNum<uint32_t>(weightSplit[1]));
        }
    }
    return retval;
}

RankResult
FtUtil::toRankResult(const vespalib::string & baseName, const vespalib::string & result, const vespalib::string & separator)
{
    RankResult retval;
    std::vector<vespalib::string> prepResult = FtUtil::tokenize(result, separator);
    for (const auto & str : prepResult) {
        std::vector<vespalib::string> rs = FtUtil::tokenize(str, ":");
        vespalib::string name = rs[0];
        vespalib::string value = rs[1];
        retval.addScore(baseName + "." + name, search::features::util::strToNum<feature_t>(value));
    }
    return retval;
}

FtIndex::~FtIndex() = default;
