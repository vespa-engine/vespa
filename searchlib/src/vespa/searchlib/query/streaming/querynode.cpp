// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
#include <vespa/searchlib/query/streaming/wand_term.h>
#include <vespa/searchlib/query/streaming/weighted_set_term.h>
#include <vespa/searchlib/query/tree/term_vector.h>
#include <charconv>
#include <vespa/log/log.h>
LOG_SETUP(".vsm.querynode");

namespace search::streaming {

namespace {

bool disableRewrite(const QueryNode * qn) {
    return dynamic_cast<const NearQueryNode *> (qn) ||
           dynamic_cast<const PhraseQueryNode *> (qn) ||
           dynamic_cast<const SameElementQueryNode *>(qn);
}

bool possibleFloat(const QueryTerm & qt, const QueryTerm::string & term) {
    return !qt.encoding().isBase10Integer() && qt.encoding().isFloat() && (term.find('.') != QueryTerm::string::npos);
}

}

QueryNode::UP
QueryNode::Build(const QueryNode * parent, const QueryNodeResultFactory & factory,
                 SimpleQueryStackDumpIterator & queryRep, bool allowRewrite)
{
    unsigned int arity = queryRep.getArity();
    ParseItem::ItemType type = queryRep.getType();
    UP qn;
    switch (type) {
    case ParseItem::ITEM_AND:
    case ParseItem::ITEM_OR:
    case ParseItem::ITEM_WEAK_AND:
    case ParseItem::ITEM_NOT:
    case ParseItem::ITEM_SAME_ELEMENT:
    case ParseItem::ITEM_NEAR:
    case ParseItem::ITEM_ONEAR:
    case ParseItem::ITEM_RANK:
    {
        qn = QueryConnector::create(type);
        if (qn) {
            auto * qc = dynamic_cast<QueryConnector *> (qn.get());
            auto * nqn = dynamic_cast<NearQueryNode *> (qc);
            if (nqn) {
                nqn->distance(queryRep.getNearDistance());
            }
            if ((type == ParseItem::ITEM_WEAK_AND) ||
                (type == ParseItem::ITEM_SAME_ELEMENT))
            {
                qn->setIndex(queryRep.getIndexName());
            }
            for (size_t i=0; i < arity; i++) {
                queryRep.next();
                if (qc->isFlattenable(queryRep.getType())) {
                    arity += queryRep.getArity();
                } else {
                    qc->addChild(Build(qc, factory, queryRep, allowRewrite && !disableRewrite(qn.get())));
                }
            }
        }
    }
    break;
    case ParseItem::ITEM_TRUE:
        qn = std::make_unique<TrueNode>();
        break;
    case ParseItem::ITEM_FALSE:
        qn = std::make_unique<FalseNode>();
        break;
    case ParseItem::ITEM_GEO_LOCATION_TERM:
        // just keep the string representation here; parsed in vsm::GeoPosFieldSearcher
        qn = std::make_unique<QueryTerm>(factory.create(), queryRep.getTerm(), queryRep.getIndexName(),
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
        vespalib::string index = queryRep.getIndexName();
        if (index.empty()) {
            if ((type == ParseItem::ITEM_PURE_WEIGHTED_STRING) || (type == ParseItem::ITEM_PURE_WEIGHTED_LONG)) {
                index = parent->getIndex();
            } else {
                index = SimpleQueryStackDumpIterator::DEFAULT_INDEX;
            }
        }
        if (dynamic_cast<const SameElementQueryNode *>(parent) != nullptr) {
            index = parent->getIndex() + "." + index;
        }
        TermType sTerm = ParseItem::toTermType(type);
        QueryTerm::string ssTerm;
        if (type == ParseItem::ITEM_PURE_WEIGHTED_LONG) {
            char buf[24];
            auto res = std::to_chars(buf, buf + sizeof(buf), queryRep.getIntergerTerm(), 10);
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
                                                 queryRep.getFuzzyMaxEditDistance(), queryRep.getFuzzyPrefixLength());
            } else [[likely]] {
                qt = std::make_unique<QueryTerm>(factory.create(), ssTerm, ssIndex, sTerm, normalize_mode);
            }
            qt->setWeight(queryRep.GetWeight());
            qt->setUniqueId(queryRep.getUniqueId());
            if (allowRewrite && possibleFloat(*qt, ssTerm) && factory.allow_float_terms_rewrite(ssIndex)) {
                auto phrase = std::make_unique<PhraseQueryNode>(factory.create(), ssIndex, arity);
                auto dotPos = ssTerm.find('.');
                phrase->add_term(std::make_unique<QueryTerm>(factory.create(), ssTerm.substr(0, dotPos), ssIndex, TermType::WORD, normalize_mode));
                phrase->add_term(std::make_unique<QueryTerm>(factory.create(), ssTerm.substr(dotPos + 1), ssIndex, TermType::WORD, normalize_mode));
                auto eqn = std::make_unique<EquivQueryNode>(factory.create(), 2);
                eqn->add_term(std::move(qt));
                eqn->add_term(std::move(phrase));
                qn = std::move(eqn);
            } else {
                qn = std::move(qt);
            }
        }
    }
    break;
    case ParseItem::ITEM_STRING_IN:
        qn = std::make_unique<InTerm>(factory.create(), queryRep.getIndexName(), queryRep.get_terms(),
                                      factory.normalizing_mode(queryRep.getIndexName()));
        break;
    case ParseItem::ITEM_NUMERIC_IN:
        qn = std::make_unique<InTerm>(factory.create(), queryRep.getIndexName(), queryRep.get_terms(), Normalizing::NONE);
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
    default:
        skip_unknown(queryRep);
        break;
    }
    return qn;
}

const HitList & QueryNode::evaluateHits(HitList & hl) const
{
    return hl;
}

std::unique_ptr<QueryNode>
QueryNode::build_nearest_neighbor_query_node(const QueryNodeResultFactory& factory, SimpleQueryStackDumpIterator& query_rep)
{
    vespalib::stringref query_tensor_name = query_rep.getTerm();
    vespalib::stringref field_name = query_rep.getIndexName();
    int32_t unique_id = query_rep.getUniqueId();
    auto weight = query_rep.GetWeight();
    uint32_t target_hits = query_rep.getTargetHits();
    double distance_threshold = query_rep.getDistanceThreshold();
    return std::make_unique<NearestNeighborQueryNode>(factory.create(), query_tensor_name, field_name,
                                                      target_hits, distance_threshold, unique_id, weight);
}

void
QueryNode::populate_multi_term(Normalizing string_normalize_mode, MultiTerm& mt, SimpleQueryStackDumpIterator& queryRep)
{
    char buf[24];
    vespalib::string subterm;
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
            auto res = std::to_chars(buf, buf + sizeof(buf), queryRep.getIntergerTerm(), 10);
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
QueryNode::build_dot_product_term(const QueryNodeResultFactory& factory, SimpleQueryStackDumpIterator& queryRep)
{
    auto dp = std::make_unique<DotProductTerm>(factory.create(), queryRep.getIndexName(), queryRep.getArity());
    dp->setWeight(queryRep.GetWeight());
    dp->setUniqueId(queryRep.getUniqueId());
    populate_multi_term(factory.normalizing_mode(dp->index()), *dp, queryRep);
    return dp;
}

std::unique_ptr<QueryNode>
QueryNode::build_wand_term(const QueryNodeResultFactory& factory, SimpleQueryStackDumpIterator& queryRep)
{
    auto wand = std::make_unique<WandTerm>(factory.create(), queryRep.getIndexName(), queryRep.getArity());
    wand->setWeight(queryRep.GetWeight());
    wand->setUniqueId(queryRep.getUniqueId());
    wand->set_score_threshold(queryRep.getScoreThreshold());
    populate_multi_term(factory.normalizing_mode(wand->index()), *wand, queryRep);
    return wand;
}

std::unique_ptr<QueryNode>
QueryNode::build_weighted_set_term(const QueryNodeResultFactory& factory, SimpleQueryStackDumpIterator& queryRep)
{
    auto ws = std::make_unique<WeightedSetTerm>(factory.create(), queryRep.getIndexName(), queryRep.getArity());
    ws->setWeight(queryRep.GetWeight());
    ws->setUniqueId(queryRep.getUniqueId());
    populate_multi_term(factory.normalizing_mode(ws->index()), *ws, queryRep);
    return ws;
}

std::unique_ptr<QueryNode>
QueryNode::build_phrase_term(const QueryNodeResultFactory& factory, SimpleQueryStackDumpIterator& queryRep)
{
    vespalib::string index = queryRep.getIndexName();
    if (index.empty()) {
        index = SimpleQueryStackDumpIterator::DEFAULT_INDEX;
    }
    auto phrase = std::make_unique<PhraseQueryNode>(factory.create(), index, queryRep.getArity());
    auto arity = queryRep.getArity();
    phrase->setWeight(queryRep.GetWeight());
    phrase->setUniqueId(queryRep.getUniqueId());
    for (size_t i = 0; i < arity; ++i) {
        queryRep.next();
        auto qn = Build(phrase.get(), factory, queryRep, false);
        auto qtp = dynamic_cast<QueryTerm*>(qn.get());
        assert(qtp != nullptr);
        qn.release();
        std::unique_ptr<QueryTerm> qt(qtp);
        phrase->add_term(std::move(qt));
    }
    return phrase;
}

std::unique_ptr<QueryNode>
QueryNode::build_equiv_term(const QueryNodeResultFactory& factory, SimpleQueryStackDumpIterator& queryRep, bool allow_rewrite)
{
    auto eqn = std::make_unique<EquivQueryNode>(factory.create(), queryRep.getArity());
    auto arity = queryRep.getArity();
    eqn->setWeight(queryRep.GetWeight());
    eqn->setUniqueId(queryRep.getUniqueId());
    for (size_t i = 0; i < arity; ++i) {
        queryRep.next();
        auto qn = Build(eqn.get(), factory, queryRep, allow_rewrite);
        auto nested_eqn = dynamic_cast<EquivQueryNode*>(qn.get());
        if (nested_eqn != nullptr) {
            auto stolen_terms = nested_eqn->steal_terms();
            for (auto& term : stolen_terms) {
                eqn->add_term(std::move(term));
            }
            continue;
        }
        auto qtp = dynamic_cast<QueryTerm*>(qn.get());
        assert(qtp != nullptr);
        qn.release();
        std::unique_ptr<QueryTerm> qt(qtp);
        eqn->add_term(std::move(qt));
    }
    return eqn;
}

void
QueryNode::skip_unknown(SimpleQueryStackDumpIterator& queryRep)
{
    auto type = queryRep.getType();
    for (uint32_t skipCount = queryRep.getArity(); (skipCount > 0) && queryRep.next(); skipCount--) {
        skipCount += queryRep.getArity();
        LOG(warning, "Does not understand anything,.... skipping %d", type);
    }
}

}
