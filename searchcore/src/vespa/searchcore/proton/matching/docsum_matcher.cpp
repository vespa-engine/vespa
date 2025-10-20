// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsum_matcher.h"
#include "match_tools.h"
#include "search_session.h"
#include "extract_features.h"
#include "field_id_to_name_mapper.h"
#include <vespa/searchcommon/attribute/i_search_context.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/equiv_blueprint.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/queryeval/isourceselector.h>
#include <vespa/searchlib/queryeval/matching_elements_search.h>
#include <vespa/searchlib/queryeval/same_element_blueprint.h>
#include <vespa/searchlib/queryeval/same_element_search.h>
#include <vespa/searchlib/fef/feature_resolver.h>
#include <vespa/searchlib/fef/rank_program.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.matching.docsum_matcher");

using search::MatchingElements;
using search::MatchingElementsFields;
using search::fef::FeatureResolver;
using search::fef::MatchData;
using search::fef::RankProgram;
using search::queryeval::AndNotBlueprint;
using search::queryeval::Blueprint;
using search::queryeval::EquivBlueprint;
using search::queryeval::IntermediateBlueprint;
using search::queryeval::MatchingElementsSearch;
using search::queryeval::MatchingPhase;
using search::queryeval::SameElementBlueprint;
using search::queryeval::SearchIterator;
using search::queryeval::SourceBlenderBlueprint;
using vespalib::FeatureSet;

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
        mtf.query().set_matching_phase(MatchingPhase::SUMMARY_FEATURES);
        matchTools->setup_summary();
    } else {
        mtf.query().set_matching_phase(MatchingPhase::DUMP_FEATURES);
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

void find_matching_elements(const std::vector<uint32_t> &docs,
                            const SameElementBlueprint &same_element,
                            MatchData& md,
                            MatchingElements &result)
{
    search::fef::TermFieldMatchData dummy_tfmd;
    auto search = same_element.create_same_element_search(md, dummy_tfmd);
    search->initRange(docs.front(), docs.back()+1);
    std::vector<uint32_t> matches;
    for (uint32_t doc : docs) {
        search->find_matching_elements(doc, matches);
        if (!matches.empty()) {
            result.add_matching_elements(doc, same_element.field_name(), matches);
            matches.clear();
        }
    }
}

void find_matching_elements(const std::vector<uint32_t> &docs,
                            MatchingElementsSearch &search,
                            MatchingElements &result)
{
    search.initRange(docs.front(), docs.back() + 1);
    for (uint32_t doc : docs) {
        search.find_matching_elements(doc, result);
    }
}

void find_matching_elements(const std::vector<uint32_t> &docs,
                            SearchIterator& search,
                            const std::string &fieldName,
                            MatchingElements &result)
{
    search.initRange(docs.front(), docs.back() + 1);
    std::vector<uint32_t> matches;
    for (uint32_t doc : docs) {
        if (search.seek(doc)) {
            search.get_element_ids(doc, matches);
            result.add_matching_elements(doc, fieldName, matches);
            matches.clear();
        }
    }
}

void find_matching_elements(const std::vector<uint32_t> &docs,
                            const std::string &field_name,
                            const AttrSearchCtx &attr_ctx,
                            MatchingElements &result) {
    int32_t weight = 0;
    std::vector<uint32_t> matches;
    for (uint32_t doc : docs) {
        for (int32_t id = attr_ctx.find(doc, 0, weight); id >= 0; id = attr_ctx.find(doc, id+1, weight)) {
            matches.push_back(id);
        }
        if (!matches.empty()) {
            result.add_matching_elements(doc, field_name, matches);
            matches.clear();
        }
    }
}

struct FindMatchingElements {
    const MatchingElementsFields &fields;
    MatchingElements &result;
    const FieldIdToNameMapper idToName;
    MatchData&                matchData;

    void process(const std::vector<uint32_t> &docs, const Blueprint &bp);
};

void FindMatchingElements::process(
        const std::vector<uint32_t> &docs,
        const Blueprint &bp)
{
    if (auto same_element = as<SameElementBlueprint>(bp)) {
        if (fields.has_field(same_element->field_name())) {
            find_matching_elements(docs, *same_element, matchData, result);
        }
    } else if (auto matching_elements_search = bp.create_matching_elements_search(fields)) {
        find_matching_elements(docs, *matching_elements_search, result);
    } else if (const AttrSearchCtx *attr_ctx = bp.get_attribute_search_context()) {
        if (fields.has_field(attr_ctx->attributeName())) {
            find_matching_elements(docs, fields.enclosing_field(attr_ctx->attributeName()), *attr_ctx, result);
        }
    } else if (auto and_not = as<AndNotBlueprint>(bp)) {
        process(docs, and_not->getChild(0));
    } else if (auto source_blender = as<SourceBlenderBlueprint>(bp)) {
        const auto & selector = source_blender->getSelector();
        auto iterator = selector.createIterator();
        for (size_t i = 0; i < source_blender->childCnt(); ++i) {
            auto &child_bp = source_blender->getChild(i);
            std::vector<uint32_t> child_docs;
            for (uint32_t docid : docs) {
                uint8_t doc_source = iterator->getSource(docid);
                if (child_bp.getSourceId() == doc_source) {
                    child_docs.push_back(docid);
                }
            }
            if (! child_docs.empty()) {
                process(child_docs, child_bp);
            }
        }
    } else if (auto intermediate = as<IntermediateBlueprint>(bp)) {
        for (size_t i = 0; i < intermediate->childCnt(); ++i) {
            process(docs, intermediate->getChild(i));
        }
    } else if (bp.getState().numFields() > 1) {
        if (auto equiv = as<EquivBlueprint>(bp)) {
            for (const auto& child_bp : equiv->childrenTerms()) {
                process(docs, *child_bp);
            }
        }
    } else if (bp.getState().numFields() == 1) {
        uint32_t currentField = bp.getState().field(0).getFieldId();
        const std::string& fieldName = idToName.lookup(currentField);
        if (fields.has_field(fieldName)) {
            SearchIterator::UP child = bp.createSearch(matchData);
            find_matching_elements(docs, *child, fieldName, result);
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
            auto match_data = _mtf->createMatchData();
            FindMatchingElements finder(fields, *result, _mtf->getFieldIdToNameMapper(), *match_data);
            finder.process(_docs, *root);
        }
    }
    return result;
}

}
