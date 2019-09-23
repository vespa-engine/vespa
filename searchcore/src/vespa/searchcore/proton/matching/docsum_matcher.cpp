// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsum_matcher.h"
#include <vespa/eval/eval/tensor.h>
#include <vespa/eval/eval/tensor_engine.h>
#include <vespa/vespalib/objects/nbostream.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.matching.docsum_matcher");

using search::FeatureSet;
using search::StructFieldMapper;
using search::MatchingElements;
using search::fef::RankProgram;
using search::fef::FeatureResolver;
using search::queryeval::SearchIterator;

namespace proton::matching {

namespace {

FeatureSet::UP get_feature_set(const MatchToolsFactory &mtf,
                               const std::vector<uint32_t> &docs,
                               bool summaryFeatures)
{
    MatchTools::UP matchTools = mtf.createMatchTools();
    if (summaryFeatures) {
        matchTools->setup_summary();
    } else {
        matchTools->setup_dump();
    }
    RankProgram &rankProgram = matchTools->rank_program();

    std::vector<vespalib::string> featureNames;
    FeatureResolver resolver(rankProgram.get_seeds(false));
    featureNames.reserve(resolver.num_features());
    for (size_t i = 0; i < resolver.num_features(); ++i) {
        featureNames.emplace_back(resolver.name_of(i));
    }
    auto retval = std::make_unique<FeatureSet>(featureNames, docs.size());
    if (docs.empty()) {
        return retval;
    }
    FeatureSet &fs = *retval;

    SearchIterator &search = matchTools->search();
    search.initRange(docs.front(), docs.back()+1);
    for (uint32_t i = 0; i < docs.size(); ++i) {
        if (search.seek(docs[i])) {
            uint32_t docId = search.getDocId();
            search.unpack(docId);
            auto * f = fs.getFeaturesByIndex(fs.addDocId(docId));
            for (uint32_t j = 0; j < featureNames.size(); ++j) {
                if (resolver.is_object(j)) {
                    auto obj = resolver.resolve(j).as_object(docId);
                    if (const auto *tensor = obj.get().as_tensor()) {
                        vespalib::nbostream buf;
                        tensor->engine().encode(*tensor, buf);
                        f[j].set_data(vespalib::Memory(buf.peek(), buf.size()));
                    } else {
                        f[j].set_double(obj.get().as_double());
                    }
                } else {
                    f[j].set_double(resolver.resolve(j).as_number(docId));
                }
            }
        } else {
            LOG(debug, "getFeatureSet: Did not find hit for docid '%u'. Skipping hit", docs[i]);
        }
    }
    if (auto onSummaryTask = mtf.createOnSummaryTask()) {
        onSummaryTask->run(docs);
    }
    return retval;
}

}

DocsumMatcher::DocsumMatcher()
    : _from_session(),
      _from_mtf(),
      _mtf(nullptr),
      _docs()
{
}

DocsumMatcher::DocsumMatcher(SearchSession::SP session, std::vector<uint32_t> docs)
    : _from_session(std::move(session)),
      _from_mtf(),
      _mtf(&_from_session->getMatchToolsFactory()),
      _docs(std::move(docs))
{
}

DocsumMatcher::DocsumMatcher(MatchToolsFactory::UP mtf, std::vector<uint32_t> docs)
    : _from_session(),
      _from_mtf(std::move(mtf)),
      _mtf(_from_mtf.get()),
      _docs(std::move(docs))
{
}

DocsumMatcher::~DocsumMatcher() {
    if (_from_session) {
        _from_session->releaseEnumGuards();
    }
}

FeatureSet::UP
DocsumMatcher::get_summary_features() const
{
    if (!_mtf) {
        return std::make_unique<FeatureSet>();
    }
    return get_feature_set(*_mtf, _docs, true);
}

FeatureSet::UP
DocsumMatcher::get_rank_features() const
{
    if (!_mtf) {
        return std::make_unique<FeatureSet>();
    }
    return get_feature_set(*_mtf, _docs, false);
}

MatchingElements::UP
DocsumMatcher::get_matching_elements(const StructFieldMapper &field_mapper) const
{
    if (!_mtf) {
        return std::make_unique<MatchingElements>();
    }
    (void) field_mapper;
    return std::make_unique<MatchingElements>();
}

}
