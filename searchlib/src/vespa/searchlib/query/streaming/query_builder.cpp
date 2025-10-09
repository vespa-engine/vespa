// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "query_builder.h"
#include "fuzzy_term.h"
#include "near_query_node.h"
#include "nearest_neighbor_query_node.h"
#include "phrase_query_node.h"
#include "query.h"
#include "regexp_term.h"
#include "same_element_query_node.h"
#include <vespa/searchlib/parsequery/stackdumpiterator.h>
#include <vespa/searchlib/query/streaming/dot_product_term.h>
#include <vespa/searchlib/query/streaming/equiv_query_node.h>
#include <vespa/searchlib/query/streaming/in_term.h>
#include <vespa/searchlib/query/streaming/same_element_query_node.h>
#include <vespa/searchlib/query/streaming/wand_term.h>
#include <vespa/searchlib/query/streaming/weighted_set_term.h>
#include <vespa/searchlib/query/tree/term_vector.h>
#include <vespa/searchlib/queryeval/split_float.h>
#include <charconv>
#include <vespa/log/log.h>
LOG_SETUP(".searchlib.query.streaming.query_builder");

using search::queryeval::SplitFloat;

namespace search::streaming {

namespace {

bool disableRewrite(const QueryNode * qn) {
    return dynamic_cast<const NearQueryNode *> (qn);
}

bool possibleFloat(const QueryTerm & qt, const QueryTerm::string & term) {
    return qt.encoding().isFloat() && ((term.find('.') != QueryTerm::string::npos) || (term.find('-') != QueryTerm::string::npos));
}

}

class QueryBuilder::HiddenTermsGuard {
    QueryBuilder &_builder;
    bool          _active;

public:
    HiddenTermsGuard(QueryBuilder& builder)
        : _builder(builder),
          _active(false)
    {
    }
    ~HiddenTermsGuard() {
        deactivate();
    }
    void activate() {
        if (!_active) {
            _active = true;
            ++_builder._hidden_terms;
        }
    }
    void deactivate() {
        if (_active) {
            _active = false;
            --_builder._hidden_terms;
        }
    }
};

QueryBuilder::QueryBuilder()
    : _same_element_view(),
      _hidden_terms(0u)
{
}

QueryBuilder::~QueryBuilder() = default;

void
QueryBuilder::adjust_index(std::string& index)
{
    if (index.empty()) {
        if (_same_element_view.has_value() && !_same_element_view.value().empty()) {
            index = _same_element_view.value();
        } else {
            index = QueryStackIterator::DEFAULT_INDEX;
        }
    } else if (_same_element_view.has_value() && !_same_element_view.value().empty()) {
        index = _same_element_view.value() + "." + index;
    }
}

std::unique_ptr<QueryNode>
QueryBuilder::build(const QueryNode * parent, const QueryNodeResultFactory& factory, QueryStackIterator & queryRep, bool allowRewrite)
{
    unsigned int arity = queryRep.getArity();
    ParseItem::ItemType type = queryRep.getType();
    std::unique_ptr<QueryNode> qn;
    switch (type) {
        case ParseItem::ITEM_AND:
        case ParseItem::ITEM_OR:
        case ParseItem::ITEM_WEAK_AND:
        case ParseItem::ITEM_NEAR:
        case ParseItem::ITEM_ONEAR:
        case ParseItem::ITEM_RANK:
        {
            qn = QueryConnector::create(type, factory);
            if (qn) {
                auto * qc = dynamic_cast<QueryConnector *> (qn.get());
                auto * nqn = dynamic_cast<NearQueryNode *> (qc);
                if (nqn) {
                    nqn->distance(queryRep.getNearDistance());
                }
                if (type == ParseItem::ITEM_WEAK_AND) {
                    qn->setIndex(queryRep.index_as_string());
                }
                for (size_t i=0; i < arity; i++) {
                    queryRep.next();
                    if (qc->isFlattenable(queryRep.getType())) {
                        arity += queryRep.getArity();
                    } else {
                        qc->addChild(build(qc, factory, queryRep, allowRewrite && !disableRewrite(qn.get())));
                    }
                }
            }
        }
            break;
        case ParseItem::ITEM_NOT:
            qn = build_and_not(factory, queryRep);
            break;
        case ParseItem::ITEM_TRUE:
            qn = std::make_unique<TrueNode>();
            break;
        case ParseItem::ITEM_FALSE:
            qn = std::make_unique<FalseNode>();
            break;
        case ParseItem::ITEM_GEO_LOCATION_TERM:
            // just keep the string representation here; parsed in vsm::GeoPosFieldSearcher
            qn = std::make_unique<QueryTerm>(factory.create(), queryRep.getTerm(), queryRep.index_as_string(),
                                             TermType::GEO_LOCATION, Normalizing::NONE);
            break;
        case ParseItem::ITEM_NEAREST_NEIGHBOR:
            qn = build_nearest_neighbor_query_node(factory, queryRep);
            break;
        case ParseItem::ITEM_NUMTERM:
        case ParseItem::ITEM_TERM:
        case ParseItem::ITEM_PREFIXTERM:
        case ParseItem::ITEM_REGEXP:
        case ParseItem::ITEM_SUBSTRINGTERM:
        case ParseItem::ITEM_EXACTSTRINGTERM:
        case ParseItem::ITEM_SUFFIXTERM:
        case ParseItem::ITEM_PURE_WEIGHTED_STRING:
        case ParseItem::ITEM_PURE_WEIGHTED_LONG:
        case ParseItem::ITEM_FUZZY:
        {
            std::string index(queryRep.index_as_view());
            if (index.empty() &&
                ((type == ParseItem::ITEM_PURE_WEIGHTED_STRING) || (type == ParseItem::ITEM_PURE_WEIGHTED_LONG)) &&
                parent != nullptr) {
                index = parent->getIndex();
            } else {
                adjust_index(index);
            }
            TermType sTerm = ParseItem::toTermType(type);
            QueryTerm::string ssTerm;
            if (type == ParseItem::ITEM_PURE_WEIGHTED_LONG) {
                char buf[24];
                auto res = std::to_chars(buf, buf + sizeof(buf), queryRep.getIntegerTerm(), 10);
                ssTerm.assign(buf, res.ptr - buf);
            } else {
                ssTerm = queryRep.getTerm();
            }
            QueryTerm::string ssIndex(index);
            if (ssIndex == "sddocname") {
                // This is suboptimal as the term should be checked too.
                // But it will do for now as only correct sddocname queries are sent down.
                qn = std::make_unique<TrueNode>();
            } else {
                Normalizing normalize_mode = factory.normalizing_mode(ssIndex);
                std::unique_ptr<QueryTerm> qt;
                if (sTerm == TermType::REGEXP) {
                    qt = std::make_unique<RegexpTerm>(factory.create(), ssTerm, ssIndex, TermType::REGEXP, normalize_mode);
                } else if (sTerm == TermType::FUZZYTERM) {
                    qt = std::make_unique<FuzzyTerm>(factory.create(), ssTerm, ssIndex, TermType::FUZZYTERM, normalize_mode,
                                                     queryRep.fuzzy_max_edit_distance(), queryRep.fuzzy_prefix_lock_length(),
                                                     queryRep.has_prefix_match_semantics());
                } else [[likely]] {
                    qt = std::make_unique<QueryTerm>(factory.create(), ssTerm, ssIndex, sTerm, normalize_mode);
                }
                qt->setWeight(queryRep.GetWeight());
                qt->setUniqueId(queryRep.getUniqueId());
                qt->setRanked( ! queryRep.hasNoRankFlag() && !hidden_terms());
                qt->set_filter(queryRep.hasNoPositionDataFlag());
                if (allowRewrite && possibleFloat(*qt, ssTerm) && factory.allow_float_terms_rewrite(ssIndex)) {
                    /*
                     * Tokenize number term and make add alternative
                     * phrase or term when searching for numbers in string
                     * fields. See
                     * CreateBlueprintVisitorHelper::handleNumberTermAsText()
                     * for similar code used for indexed search.
                     */
                    SplitFloat splitter(ssTerm);
                    std::unique_ptr<QueryTerm> alt_qt;
                    if (splitter.parts() > 1) {
                        auto phrase = std::make_unique<PhraseQueryNode>(factory.create(), ssIndex, splitter.parts());
                        for (size_t i = 0; i < splitter.parts(); ++i) {
                            phrase->add_term(std::make_unique<QueryTerm>(factory.create(), splitter.getPart(i), ssIndex, TermType::WORD, normalize_mode));
                        }
                        alt_qt = std::move(phrase);
                    } else if (splitter.parts() == 1 && ssTerm != splitter.getPart(0)) {
                        alt_qt = std::make_unique<QueryTerm>(factory.create(), splitter.getPart(0), ssIndex, TermType::WORD, normalize_mode);
                    }
                    if (alt_qt) {
                        auto eqn = std::make_unique<EquivQueryNode>(factory.create(), 2);
                        eqn->add_term(std::move(qt));
                        eqn->add_term(std::move(alt_qt));
                        qn = std::move(eqn);
                    } else {
                        qn = std::move(qt);
                    }
                } else {
                    qn = std::move(qt);
                }
            }
        }
            break;
        case ParseItem::ITEM_STRING_IN:
            qn = std::make_unique<InTerm>(factory.create(), queryRep.index_as_string(), queryRep.get_terms(),
                                          factory.normalizing_mode(queryRep.index_as_view()));
            break;
        case ParseItem::ITEM_NUMERIC_IN:
            qn = std::make_unique<InTerm>(factory.create(), queryRep.index_as_string(), queryRep.get_terms(), Normalizing::NONE);
            break;
        case ParseItem::ITEM_DOT_PRODUCT:
            qn = build_dot_product_term(factory, queryRep);
            break;
        case ParseItem::ITEM_WAND:
            qn = build_wand_term(factory, queryRep);
            break;
        case ParseItem::ITEM_WEIGHTED_SET:
            qn = build_weighted_set_term(factory, queryRep);
            break;
        case ParseItem::ITEM_PHRASE:
            qn = build_phrase_term(factory, queryRep);
            break;
        case ParseItem::ITEM_EQUIV:
            qn = build_equiv_term(factory, queryRep, allowRewrite);
            break;
        case ParseItem::ITEM_SAME_ELEMENT:
            qn = build_same_element_term(factory, queryRep);
            break;
        default:
            skip_unknown(queryRep);
            break;
    }
    return qn;
}

std::unique_ptr<QueryNode>
QueryBuilder::build_nearest_neighbor_query_node(const QueryNodeResultFactory& factory, QueryStackIterator& query_rep)
{
    std::string_view query_tensor_name = query_rep.getTerm();
    std::string field_name = query_rep.index_as_string();
    int32_t unique_id = query_rep.getUniqueId();
    auto weight = query_rep.GetWeight();
    uint32_t target_hits = query_rep.getTargetHits();
    double distance_threshold = query_rep.getDistanceThreshold();
    return std::make_unique<NearestNeighborQueryNode>(factory.create(), query_tensor_name, field_name,
                                                      target_hits, distance_threshold, unique_id, weight);
}

void
QueryBuilder::populate_multi_term(Normalizing string_normalize_mode, MultiTerm& mt, QueryStackIterator& queryRep)
{
    char buf[24];
    std::string subterm;
    auto arity = queryRep.getArity();
    for (size_t i = 0; i < arity && queryRep.next(); i++) {
        std::unique_ptr<QueryTerm> term;
        switch (queryRep.getType()) {
            case ParseItem::ITEM_PURE_WEIGHTED_STRING:
                term = std::make_unique<QueryTerm>(std::unique_ptr<QueryNodeResultBase>(), queryRep.getTerm(), "",
                                                   QueryTermSimple::Type::WORD, string_normalize_mode);
                break;
            case ParseItem::ITEM_PURE_WEIGHTED_LONG:
            {
                auto res = std::to_chars(buf, buf + sizeof(buf), queryRep.getIntegerTerm(), 10);
                subterm.assign(buf, res.ptr - buf);
                term = std::make_unique<QueryTerm>(std::unique_ptr<QueryNodeResultBase>(), subterm, "",
                                                   QueryTermSimple::Type::WORD, Normalizing::NONE);
            }
                break;
            default:
                skip_unknown(queryRep);
                break;
        }
        if (term) {
            term->setWeight(queryRep.GetWeight());
            mt.add_term(std::move(term));
        }
    }
}

std::unique_ptr<QueryNode>
QueryBuilder::build_dot_product_term(const QueryNodeResultFactory& factory, QueryStackIterator& queryRep)
{
    auto dp = std::make_unique<DotProductTerm>(factory.create(), queryRep.index_as_string(), queryRep.getArity());
    dp->setWeight(queryRep.GetWeight());
    dp->setUniqueId(queryRep.getUniqueId());
    populate_multi_term(factory.normalizing_mode(dp->index()), *dp, queryRep);
    return dp;
}

std::unique_ptr<QueryNode>
QueryBuilder::build_wand_term(const QueryNodeResultFactory& factory, QueryStackIterator& queryRep)
{
    auto wand = std::make_unique<WandTerm>(factory.create(), queryRep.index_as_string(), queryRep.getArity());
    wand->setWeight(queryRep.GetWeight());
    wand->setUniqueId(queryRep.getUniqueId());
    wand->set_score_threshold(queryRep.getScoreThreshold());
    populate_multi_term(factory.normalizing_mode(wand->index()), *wand, queryRep);
    return wand;
}

std::unique_ptr<QueryNode>
QueryBuilder::build_weighted_set_term(const QueryNodeResultFactory& factory, QueryStackIterator& queryRep)
{
    auto ws = std::make_unique<WeightedSetTerm>(factory.create(), queryRep.index_as_string(), queryRep.getArity());
    ws->setWeight(queryRep.GetWeight());
    ws->setUniqueId(queryRep.getUniqueId());
    populate_multi_term(factory.normalizing_mode(ws->index()), *ws, queryRep);
    return ws;
}

std::unique_ptr<QueryNode>
QueryBuilder::build_phrase_term(const QueryNodeResultFactory& factory, QueryStackIterator& queryRep)
{
    std::string index(queryRep.index_as_view());
    adjust_index(index);
    auto phrase = std::make_unique<PhraseQueryNode>(factory.create(), index, queryRep.getArity());
    auto arity = queryRep.getArity();
    phrase->setWeight(queryRep.GetWeight());
    phrase->setUniqueId(queryRep.getUniqueId());
    for (size_t i = 0; i < arity; ++i) {
        queryRep.next();
        auto qn = build(phrase.get(), factory, queryRep, false);
        std::unique_ptr<QueryTerm> qt(dynamic_cast<QueryTerm*>(qn.release()));
        assert(qt);
        phrase->add_term(std::move(qt));
    }
    return phrase;
}

std::unique_ptr<QueryNode>
QueryBuilder::build_equiv_term(const QueryNodeResultFactory& factory, QueryStackIterator& queryRep, bool allow_rewrite)
{
    auto eqn = std::make_unique<EquivQueryNode>(factory.create(), queryRep.getArity());
    auto arity = queryRep.getArity();
    eqn->setWeight(queryRep.GetWeight());
    eqn->setUniqueId(queryRep.getUniqueId());
    for (size_t i = 0; i < arity; ++i) {
        queryRep.next();
        auto qn = build(eqn.get(), factory, queryRep, allow_rewrite);
        auto nested_eqn = dynamic_cast<EquivQueryNode*>(qn.get());
        if (nested_eqn != nullptr) {
            auto stolen_terms = nested_eqn->steal_terms();
            for (auto& term : stolen_terms) {
                eqn->add_term(std::move(term));
            }
            continue;
        }
        std::unique_ptr<QueryTerm> qt(dynamic_cast<QueryTerm*>(qn.release()));
        assert(qt);
        eqn->add_term(std::move(qt));
    }
    return eqn;
}

std::unique_ptr<QueryNode>
QueryBuilder::build_same_element_term(const QueryNodeResultFactory& factory, QueryStackIterator& queryRep)
{
    auto sen = std::make_unique<SameElementQueryNode>(factory.create(), queryRep.index_as_string(), queryRep.getArity());
    _same_element_view = queryRep.index_as_string();
    auto arity = queryRep.getArity();
    sen->setWeight(queryRep.GetWeight());
    sen->setUniqueId(queryRep.getUniqueId());
    for (size_t i = 0; i < arity; ++i) {
        queryRep.next();
        auto qn = build(sen.get(), factory, queryRep, false);
        sen->add_child(std::move(qn));
    }
    _same_element_view.reset();
    return sen;
}

std::unique_ptr<QueryNode>
QueryBuilder::build_and_not(const QueryNodeResultFactory& factory, QueryStackIterator& queryRep)
{
    HiddenTermsGuard hidden_terms_guard(*this);
    auto arity = queryRep.getArity();
    auto and_not = std::make_unique<AndNotQueryNode>();
    for (size_t i = 0; i < arity; ++i) {
        queryRep.next();
        if (i == 1u) {
            hidden_terms_guard.activate();
        }
        auto qn = build(and_not.get(), factory, queryRep, true);
        and_not->addChild(std::move(qn));
    }
    return and_not;
}

void
QueryBuilder::skip_unknown(QueryStackIterator& queryRep)
{
    auto type = queryRep.getType();
    for (uint32_t skipCount = queryRep.getArity(); (skipCount > 0) && queryRep.next(); skipCount--) {
        skipCount += queryRep.getArity();
        LOG(warning, "Does not understand anything,.... skipping %d", type);
    }
}

}
