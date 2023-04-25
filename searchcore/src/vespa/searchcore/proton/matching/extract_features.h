// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/stringmap.h>
#include <vespa/vespalib/util/featureset.h>
#include <vector>

namespace vespalib { class Doom; };
namespace vespalib { struct ThreadBundle; };
namespace search::queryeval { class SearchIterator; }
namespace search::fef { class RankProgram; }

namespace proton::matching {

class MatchToolsFactory;

struct ExtractFeatures {
    using FeatureSet = vespalib::FeatureSet;
    using FeatureValues = vespalib::FeatureValues;
    using ThreadBundle = vespalib::ThreadBundle;
    using SearchIterator = search::queryeval::SearchIterator;
    using RankProgram = search::fef::RankProgram;
    using StringStringMap = search::StringStringMap;

    /**
     * Extract all seed features from a rank program for a list of
     * documents (must be in ascending order) using unpack information
     * from a search.
     **/
    static FeatureSet::UP get_feature_set(SearchIterator &search, RankProgram &rank_program, const std::vector<uint32_t> &docs, const vespalib::Doom &doom, const StringStringMap &renames);

    // first: docid, second: result index (must be sorted on docid)
    using OrderedDocs = std::vector<std::pair<uint32_t,uint32_t>>;

    /**
     * Extract match features using multiple threads.
     **/
    static FeatureValues get_match_features(const MatchToolsFactory &mtf, const OrderedDocs &docs, ThreadBundle &thread_bundle);
};

}
