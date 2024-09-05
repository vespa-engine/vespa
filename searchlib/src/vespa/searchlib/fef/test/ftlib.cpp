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

FtFeatureTest::FtFeatureTest(search::fef::BlueprintFactory &factory, const std::string &feature) :
    _indexEnv(),
    _queryEnv(_indexEnv),
    _overrides(),
    _test(factory, _indexEnv, _queryEnv, _queryEnv.getLayout(), feature, _overrides)
{
}

FtFeatureTest::FtFeatureTest(search::fef::BlueprintFactory &factory, const std::vector<std::string> &features)
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
std::vector<std::string>
FtUtil::tokenize(const std::string & str, const std::string & separator)
{
    std::vector<std::string> retval;
    if (separator != std::string("")) {
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
FtUtil::toQuery(const std::string & query, const std::string & separator)
{
    std::vector<std::string> prepQuery = FtUtil::tokenize(query, separator);
    FtQuery retval(prepQuery.size());
    for (uint32_t i = 0; i < prepQuery.size(); ++i) {
        std::vector<std::string> significanceSplit = FtUtil::tokenize(prepQuery[i], std::string("%"));
        std::vector<std::string> weightSplit = FtUtil::tokenize(significanceSplit[0], std::string("!"));
        std::vector<std::string> connexitySplit = FtUtil::tokenize(weightSplit[0], std::string(":"));
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
FtUtil::toRankResult(const std::string & baseName, const std::string & result, const std::string & separator)
{
    RankResult retval;
    std::vector<std::string> prepResult = FtUtil::tokenize(result, separator);
    for (const auto & str : prepResult) {
        std::vector<std::string> rs = FtUtil::tokenize(str, ":");
        std::string name = rs[0];
        std::string value = rs[1];
        retval.addScore(baseName + "." + name, search::features::util::strToNum<feature_t>(value));
    }
    return retval;
}

FtIndex::~FtIndex() = default;
