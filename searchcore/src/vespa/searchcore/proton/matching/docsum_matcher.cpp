// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsum_matcher.h"
#include "match_tools.h"
#include "search_session.h"
#include "extract_features.h"
#include <vespa/eval/eval/value_codec.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/searchcommon/attribute/i_search_context.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/queryeval/same_element_blueprint.h>
#include <vespa/searchlib/queryeval/same_element_search.h>
#include <vespa/searchlib/queryeval/matching_elements_search.h>
#include <vespa/searchlib/fef/feature_resolver.h>
#include <vespa/searchlib/fef/rank_program.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.matching.docsum_matcher");

using search::FeatureSet;
using search::MatchingElements;
using search::MatchingElementsFields;
using search::fef::FeatureResolver;
using search::fef::RankProgram;
using search::queryeval::AndNotBlueprint;
using search::queryeval::Blueprint;
using search::queryeval::IntermediateBlueprint;
using search::queryeval::MatchingElementsSearch;
using search::queryeval::SameElementBlueprint;
using search::queryeval::SearchIterator;

using AttrSearchCtx = search::attribute::ISearchContext;

namespace proton::matching {

namespace {

FeatureSet::UP
get_feature_set(const MatchToolsFactory &mtf,
                const std::vector<uint32_t> &docs,
                bool summaryFeatures)
{
    MatchTools::UP matchTools = mtf.createMatchTools();
    if (summaryFeatures) {
        matchTools->setup_summary();
    } else {
        matchTools->setup_dump();
    }
    auto retval = ExtractFeatures::get_feature_set(matchTools->search(), matchTools->rank_program(), docs,
                                                   matchTools->getDoom(), mtf.get_feature_rename_map());
    if (auto onSummaryTask = mtf.createOnSummaryTask()) {
        onSummaryTask->run(docs);
    }
    return retval;
}

template<typename T>
const T *as(const Blueprint &bp) { return dynamic_cast<const T *>(&bp); }

void find_matching_elements(const std::vector<uint32_t> &docs, const SameElementBlueprint &same_element, MatchingElements &result) {
    search::fef::TermFieldMatchData dummy_tfmd;
    auto search = same_element.create_same_element_search(dummy_tfmd, false);
    search->initRange(docs.front(), docs.back()+1);
    std::vector<uint32_t> matches;
    for (uint32_t i = 0; i < docs.size(); ++i) {
        search->find_matching_elements(docs[i], matches);
        if (!matches.empty()) {
            result.add_matching_elements(docs[i], same_element.field_name(), matches);
            matches.clear();
        }
    }
}

void find_matching_elements(const std::vector<uint32_t> &docs, MatchingElementsSearch &search, MatchingElements &result) {
    search.initRange(docs.front(), docs.back() + 1);
    for (uint32_t i = 0; i < docs.size(); ++i) {
        search.find_matching_elements(docs[i], result);
    }
}

void find_matching_elements(const std::vector<uint32_t> &docs, const vespalib::string &field_name, const AttrSearchCtx &attr_ctx, MatchingElements &result) {
    int32_t weight = 0;
    std::vector<uint32_t> matches;
    for (uint32_t i = 0; i < docs.size(); ++i) {
        for (int32_t id = attr_ctx.find(docs[i], 0, weight); id >= 0; id = attr_ctx.find(docs[i], id+1, weight)) {
            matches.push_back(id);
        }
        if (!matches.empty()) {
            result.add_matching_elements(docs[i], field_name, matches);
            matches.clear();
        }
    }
}

void find_matching_elements(const MatchingElementsFields &fields, const std::vector<uint32_t> &docs, const Blueprint &bp, MatchingElements &result) {
    if (auto same_element = as<SameElementBlueprint>(bp)) {
        if (fields.has_field(same_element->field_name())) {
            find_matching_elements(docs, *same_element, result);
        }
    } else if (auto matching_elements_search = bp.create_matching_elements_search(fields)) {
        find_matching_elements(docs, *matching_elements_search, result);
    } else if (const AttrSearchCtx *attr_ctx = bp.get_attribute_search_context()) {
        if (fields.has_struct_field(attr_ctx->attributeName())) {
            find_matching_elements(docs, fields.get_enclosing_field(attr_ctx->attributeName()), *attr_ctx, result);
        } else if (fields.has_field(attr_ctx->attributeName())) {
            find_matching_elements(docs, attr_ctx->attributeName(), *attr_ctx, result);
        }
    } else if (auto and_not = as<AndNotBlueprint>(bp)) {
        find_matching_elements(fields, docs, and_not->getChild(0), result);
    } else if (auto intermediate = as<IntermediateBlueprint>(bp)) {
        for (size_t i = 0; i < intermediate->childCnt(); ++i) {
            find_matching_elements(fields, docs, intermediate->getChild(i), result);
        }
    }
}

}

DocsumMatcher::DocsumMatcher()
    : _from_session(),
      _from_mtf(),
      _mtf(nullptr),
      _docs()
{
}

DocsumMatcher::DocsumMatcher(std::shared_ptr<SearchSession> session, std::vector<uint32_t> docs)
    : _from_session(std::move(session)),
      _from_mtf(),
      _mtf(&_from_session->getMatchToolsFactory()),
      _docs(std::move(docs))
{
}

DocsumMatcher::DocsumMatcher(std::unique_ptr<MatchToolsFactory> mtf, std::vector<uint32_t> docs)
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
DocsumMatcher::get_matching_elements(const MatchingElementsFields &fields) const
{
    auto result = std::make_unique<MatchingElements>();
    if (_mtf && !fields.empty()) {
        if (const Blueprint *root = _mtf->query().peekRoot()) {
            find_matching_elements(fields, _docs, *root, *result);
        }
    }
    return result;
}

}
