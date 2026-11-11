// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "extract_features.h"

#include "match_tools.h"

#include <vespa/check_require.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/searchlib/fef/feature_resolver.h>
#include <vespa/searchlib/fef/rank_program.h>
#include <vespa/searchlib/fef/utils.h>
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/doom.h>
#include <vespa/vespalib/util/runnable.h>
#include <vespa/vespalib/util/thread_bundle.h>

using search::fef::FeatureResolver;
using search::fef::RankProgram;
using search::queryeval::MatchingPhase;
using search::queryeval::SearchIterator;
using vespalib::Doom;
using vespalib::FeatureSet;
using vespalib::FeatureValues;
using vespalib::Runnable;
using vespalib::ThreadBundle;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::TensorSpec;

namespace proton::matching {

using OrderedDocs = ExtractFeatures::OrderedDocs;
using search::StringStringMap;

using FefUtils = search::fef::Utils;

namespace {

using DefaultValues = std::vector<FeatureValues::Value>;

/**
 * Values for the match features of hits that are not reached before the hard
 * timeout: 0.0 for numbers and a tensor with all cells 0 for tensors, so that
 * every value has the type of its feature.
 **/
DefaultValues make_default_values(const FeatureResolver& resolver) {
    DefaultValues defaults(resolver.num_features());
    for (size_t i = 0; i < resolver.num_features(); ++i) {
        const auto* type = resolver.type_of(i);
        if (type != nullptr && !type->is_double()) {
            auto                zero = value_from_spec(TensorSpec(type->to_spec()), FastValueBuilderFactory::get());
            vespalib::nbostream buf;
            encode_value(*zero, buf);
            defaults[i].set_data(vespalib::Memory(buf.peek(), buf.size()));
        }
    }
    return defaults;
}

struct MyChunk : Runnable {
    const std::pair<uint32_t, uint32_t>* begin;
    const std::pair<uint32_t, uint32_t>* end;
    FeatureValues&                       result;
    const Doom&                          doom;
    const DefaultValues&                 defaults;
    MyChunk(const std::pair<uint32_t, uint32_t>* begin_in, const std::pair<uint32_t, uint32_t>* end_in,
            FeatureValues& result_in, const Doom& doom_in, const DefaultValues& defaults_in)
        : begin(begin_in), end(end_in), result(result_in), doom(doom_in), defaults(defaults_in) {}
    void calculate_features(SearchIterator& search, const FeatureResolver& resolver) {
        CHECK(end > begin);
        CHECK(resolver.num_features() == result.names.size());
        search.initRange(begin[0].first, end[-1].first + 1);
        for (auto pos = begin; pos != end; ++pos) {
            if (doom.hard_doom()) {
                fill_with_defaults(pos);
                return;
            }
            search.unpack(pos->first);
            auto* dst = &result.values[pos->second * resolver.num_features()];
            FefUtils::extract_feature_values(resolver, pos->first, dst);
        }
    }
    // Gives the remaining hits of this chunk the default values of their match features.
    void fill_with_defaults(const std::pair<uint32_t, uint32_t>* from) {
        CHECK(defaults.size() == result.names.size());
        for (auto pos = from; pos != end; ++pos) {
            std::copy(defaults.begin(), defaults.end(), &result.values[pos->second * defaults.size()]);
        }
    }
};

struct FirstChunk : MyChunk {
    SearchIterator&        search;
    const FeatureResolver& resolver;
    FirstChunk(const std::pair<uint32_t, uint32_t>* begin_in, const std::pair<uint32_t, uint32_t>* end_in,
               FeatureValues& result_in, const Doom& doom_in, const DefaultValues& defaults_in,
               SearchIterator& search_in, const FeatureResolver& resolver_in)
        : MyChunk(begin_in, end_in, result_in, doom_in, defaults_in), search(search_in), resolver(resolver_in) {}
    void run() override { calculate_features(search, resolver); }
};

struct LaterChunk : MyChunk {
    const MatchToolsFactory& mtf;
    LaterChunk(const std::pair<uint32_t, uint32_t>* begin_in, const std::pair<uint32_t, uint32_t>* end_in,
               FeatureValues& result_in, const Doom& doom_in, const DefaultValues& defaults_in,
               const MatchToolsFactory& mtf_in)
        : MyChunk(begin_in, end_in, result_in, doom_in, defaults_in), mtf(mtf_in) {}
    void run() override {
        auto tools = mtf.createMatchTools();
        tools->setup_match_features();
        FeatureResolver resolver(tools->rank_program().get_seeds(false));
        calculate_features(tools->search(), resolver);
    }
};

} // namespace

FeatureSet::UP ExtractFeatures::get_feature_set(SearchIterator& search, RankProgram& rank_program,
                                                const std::vector<uint32_t>& docs, const Doom& doom,
                                                const StringStringMap& renames) {
    FeatureResolver resolver(rank_program.get_seeds(false));
    auto result = std::make_unique<FeatureSet>(FefUtils::extract_feature_names(resolver, renames), docs.size());
    if (!docs.empty()) {
        search.initRange(docs.front(), docs.back() + 1);
        for (uint32_t docid : docs) {
            if (doom.hard_doom()) {
                return result;
            }
            search.unpack(docid);
            auto* dst = result->getFeaturesByIndex(result->addDocId(docid));
            FefUtils::extract_feature_values(resolver, docid, dst);
        }
    }
    return result;
}

FeatureValues ExtractFeatures::get_match_features(const MatchToolsFactory& mtf, const OrderedDocs& docs,
                                                  ThreadBundle& thread_bundle) {
    FeatureValues result;
    mtf.query().set_matching_phase(MatchingPhase::MATCH_FEATURES);
    auto tools = mtf.createMatchTools();
    tools->setup_match_features();
    FeatureResolver resolver(tools->rank_program().get_seeds(false));
    result.names = FefUtils::extract_feature_names(resolver, mtf.get_feature_rename_map());
    result.values.resize(result.names.size() * docs.size());
    const DefaultValues       defaults = make_default_values(resolver);
    size_t                    num_threads = thread_bundle.size();
    std::vector<Runnable::UP> chunks;
    chunks.reserve(num_threads);
    size_t per_thread = docs.size() / num_threads;
    size_t rest_docs = docs.size() % num_threads;
    size_t idx = 0;
    for (size_t i = 0; i < num_threads; ++i) {
        size_t chunk_size = per_thread + (i < rest_docs);
        if (chunk_size == 0) {
            break;
        }
        if (i == 0) {
            chunks.push_back(std::make_unique<FirstChunk>(docs.data() + idx, docs.data() + idx + chunk_size, result,
                                                          tools->getDoom(), defaults, tools->search(), resolver));
        } else {
            chunks.push_back(std::make_unique<LaterChunk>(docs.data() + idx, docs.data() + idx + chunk_size, result,
                                                          tools->getDoom(), defaults, mtf));
        }
        idx += chunk_size;
    }
    CHECK(idx == docs.size());
    thread_bundle.run(chunks);
    return result;
}

} // namespace proton::matching
