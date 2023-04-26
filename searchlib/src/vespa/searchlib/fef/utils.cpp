// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "utils.h"
#include "rank_program.h"
#include <vespa/eval/eval/value_codec.h>
#include <vector>
#include <cassert>

using vespalib::FeatureSet;

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

std::vector<vespalib::string>
Utils::extract_feature_names(const FeatureResolver& resolver, const StringStringMap& renames)
{
    std::vector<vespalib::string> result;
    result.reserve(resolver.num_features());
    for (size_t i = 0; i < resolver.num_features(); ++i) {
        vespalib::string name = resolver.name_of(i);
        auto iter = renames.find(name);
        if (iter != renames.end()) {
            name = iter->second;
        }
        result.emplace_back(name);
    }
    return result;
}

void
Utils::extract_feature_values(const FeatureResolver& resolver, uint32_t docid, FeatureSet::Value* dst)
{
    for (uint32_t i = 0; i < resolver.num_features(); ++i) {
        if (resolver.is_object(i)) {
            auto obj = resolver.resolve(i).as_object(docid);
            if (!obj.get().type().is_double()) {
                vespalib::nbostream buf;
                encode_value(obj.get(), buf);
                dst[i].set_data(vespalib::Memory(buf.peek(), buf.size()));
            } else {
                dst[i].set_double(obj.get().as_double());
            }
        } else {
            dst[i].set_double(resolver.resolve(i).as_number(docid));
        }
    }
}

}
