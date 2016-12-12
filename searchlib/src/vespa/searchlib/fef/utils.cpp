// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "utils.h"
#include <vector>

namespace search {
namespace fef {

const feature_t *
Utils::getScoreFeature(const RankProgram &rankProgram)
{
    FeatureResolver resolver(rankProgram.get_seeds(false));
    assert(resolver.num_features() == 1u);
    return resolver.resolve_number(0);
}

const vespalib::eval::Value::CREF *
Utils::getObjectFeature(const RankProgram &rankProgram)
{
    FeatureResolver resolver(rankProgram.get_seeds(false));
    assert(resolver.num_features() == 1u);
    return resolver.resolve_object(0);
}

namespace {

std::map<vespalib::string, feature_t>
resolveFeatures(const FeatureResolver &resolver)
{
    std::map<vespalib::string, feature_t> result;
    size_t numFeatures = resolver.num_features();
    for (size_t i = 0; i < numFeatures; ++i) {
        const vespalib::string &name = resolver.name_of(i);
        feature_t value = *(resolver.resolve_number(i));
        result.insert(std::make_pair(name, value));
    }
    return result;
}

}

std::map<vespalib::string, feature_t>
Utils::getSeedFeatures(const RankProgram &rankProgram)
{
    FeatureResolver resolver(rankProgram.get_seeds());
    return resolveFeatures(resolver);
}

std::map<vespalib::string, feature_t>
Utils::getAllFeatures(const RankProgram &rankProgram)
{
    FeatureResolver resolver(rankProgram.get_all_features());
    return resolveFeatures(resolver);
}

} // namespace fef
} // namespace search
