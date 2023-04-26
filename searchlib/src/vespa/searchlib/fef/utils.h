// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/feature.h>
#include <vespa/searchlib/common/stringmap.h>
#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/util/featureset.h>
#include <map>

namespace search::fef {

class FeatureResolver;
class RankProgram;

struct Utils
{
    /**
     * Extract a single score feature from the given rank program.
     */
    static feature_t getScoreFeature(const RankProgram &rankProgram, uint32_t docid);

    /**
     * Extract a single object feature from the given rank program.
     */
    static vespalib::eval::Value::CREF getObjectFeature(const RankProgram &rankProgram, uint32_t docid);

    /**
     * Extract all seed feature values from the given rank program.
     **/
    static std::map<vespalib::string, feature_t> getSeedFeatures(const RankProgram &rankProgram, uint32_t docid);

    /**
     * Extract all feature values from the given rank program.
     **/
    static std::map<vespalib::string, feature_t> getAllFeatures(const RankProgram &rankProgram, uint32_t docid);

    /*
     * Extract features names for the given feature resolver.
     */
    std::vector<vespalib::string>
    static extract_feature_names(const FeatureResolver& resolver, const search::StringStringMap& renames);

    /*
     * Extract feature values for the given feature resolver.
     */
    static void extract_feature_values(const FeatureResolver& resolver, uint32_t docid, vespalib::FeatureSet::Value* dst);
};

}
