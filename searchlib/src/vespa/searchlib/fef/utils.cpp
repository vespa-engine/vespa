// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "utils.h"
#include <vector>

namespace search {
namespace fef {

namespace {

FeatureHandle
getSingleFeatureHandle(const RankProgram &rankProgram)
{
    std::vector<vespalib::string> featureNames;
    std::vector<FeatureHandle> featureHandles;
    rankProgram.get_seed_handles(featureNames, featureHandles, false);
    assert(featureNames.size() == 1);
    assert(featureHandles.size() == 1);
    return featureHandles.front();
}

}

const feature_t *
Utils::getScoreFeature(const RankProgram &rankProgram)
{
    return rankProgram.match_data().resolveFeature(getSingleFeatureHandle(rankProgram));
}

const vespalib::eval::Value::CREF *
Utils::getObjectFeature(const RankProgram &rankProgram)
{
    return rankProgram.match_data().resolve_object_feature(getSingleFeatureHandle(rankProgram));
}

namespace {

std::map<vespalib::string, feature_t>
resolveFeatures(const MatchData &matchData,
                const std::vector<vespalib::string> &featureNames,
                const std::vector<FeatureHandle> &featureHandles)
{
    assert(featureNames.size() == featureHandles.size());
    std::map<vespalib::string, feature_t> result;
    for (size_t i = 0; i < featureNames.size(); ++i) {
        const vespalib::string &name = featureNames[i];
        feature_t value = *(matchData.resolveFeature(featureHandles[i]));
        result.insert(std::make_pair(name, value));
    }
    return result;
}

}

std::map<vespalib::string, feature_t>
Utils::getSeedFeatures(const RankProgram &rankProgram)
{
    std::vector<vespalib::string> featureNames;
    std::vector<FeatureHandle> featureHandles;
    rankProgram.get_seed_handles(featureNames, featureHandles);
    return resolveFeatures(rankProgram.match_data(), featureNames, featureHandles);
}

std::map<vespalib::string, feature_t>
Utils::getAllFeatures(const RankProgram &rankProgram)
{
    std::vector<vespalib::string> featureNames;
    std::vector<FeatureHandle> featureHandles;
    rankProgram.get_all_feature_handles(featureNames, featureHandles);
    return resolveFeatures(rankProgram.match_data(), featureNames, featureHandles);
}

} // namespace fef
} // namespace search
