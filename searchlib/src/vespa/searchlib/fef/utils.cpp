// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "utils.h"
#include <vector>
#include <cassert>

namespace search::fef {

feature_t
Utils::getScoreFeature(const RankProgram &rankProgram, uint32_t docid)
{
    FeatureResolver resolver(rankProgram.get_seeds(false));
    assert(resolver.num_features() == 1u);
    assert(!resolver.is_object(0));
    return resolver.resolve(0).as_number(docid);
}

vespalib::eval::Value::CREF
Utils::getObjectFeature(const RankProgram &rankProgram, uint32_t docid)
{
    FeatureResolver resolver(rankProgram.get_seeds(false));
    assert(resolver.num_features() == 1u);
    assert(resolver.is_object(0));
    return resolver.resolve(0).as_object(docid);
}

namespace {

std::map<vespalib::string, feature_t>
resolveFeatures(const FeatureResolver &resolver, uint32_t docid)
{
    std::map<vespalib::string, feature_t> result;
    size_t numFeatures = resolver.num_features();
    for (size_t i = 0; i < numFeatures; ++i) {
        const vespalib::string &name = resolver.name_of(i);
        feature_t value = resolver.resolve(i).as_number(docid);
        result.insert(std::make_pair(name, value));
    }
    return result;
}

}

std::map<vespalib::string, feature_t>
Utils::getSeedFeatures(const RankProgram &rankProgram, uint32_t docid)
{
    FeatureResolver resolver(rankProgram.get_seeds());
    return resolveFeatures(resolver, docid);
}

std::map<vespalib::string, feature_t>
Utils::getAllFeatures(const RankProgram &rankProgram, uint32_t docid)
{
    FeatureResolver resolver(rankProgram.get_all_features());
    return resolveFeatures(resolver, docid);
}

}
