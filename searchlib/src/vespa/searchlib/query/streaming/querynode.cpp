// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "query.h"
#include "nearest_neighbor_query_node.h"
#include <vespa/searchlib/parsequery/stackdumpiterator.h>
#include <charconv>
#include <vespa/log/log.h>
LOG_SETUP(".vsm.querynode");

namespace search::streaming {

namespace {
    vespalib::stringref DEFAULT("default");
    bool disableRewrite(const QueryNode * qn) {
        return dynamic_cast<const NearQueryNode *> (qn) ||
               dynamic_cast<const PhraseQueryNode *> (qn) ||
               dynamic_cast<const SameElementQueryNode *>(qn);
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
    case ParseItem::ITEM_EQUIV:
    case ParseItem::ITEM_WEIGHTED_SET:
    case ParseItem::ITEM_DOT_PRODUCT:
    case ParseItem::ITEM_WAND:
    case ParseItem::ITEM_NOT:
    case ParseItem::ITEM_PHRASE:
    case ParseItem::ITEM_SAME_ELEMENT:
    case ParseItem::ITEM_NEAR:
    case ParseItem::ITEM_ONEAR:
    {
        qn = QueryConnector::create(type);
        if (qn) {
            QueryConnector * qc = dynamic_cast<QueryConnector *> (qn.get());
            NearQueryNode * nqn = dynamic_cast<NearQueryNode *> (qc);
            if (nqn) {
                nqn->distance(queryRep.getNearDistance());
            }
            if ((type == ParseItem::ITEM_WEAK_AND) ||
                (type == ParseItem::ITEM_WEIGHTED_SET) ||
                (type == ParseItem::ITEM_DOT_PRODUCT) ||
                (type == ParseItem::ITEM_SAME_ELEMENT) ||
                (type == ParseItem::ITEM_WAND))
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
        qn = std::make_unique<QueryTerm>(factory.create(),
                                         queryRep.getTerm(),
                                         queryRep.getIndexName(),
                                         QueryTerm::Type::GEO_LOCATION);
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
                index = DEFAULT;
            }
        }
        if (dynamic_cast<const SameElementQueryNode *>(parent) != nullptr) {
            index = parent->getIndex() + "." + index;
        }
        using TermType = QueryTerm::Type;
        TermType sTerm(TermType::WORD);
        switch (type) {
        case ParseItem::ITEM_REGEXP:
            sTerm = TermType::REGEXP;
            break;
        case ParseItem::ITEM_PREFIXTERM:
            sTerm = TermType::PREFIXTERM;
            break;
        case ParseItem::ITEM_SUBSTRINGTERM:
            sTerm = TermType::SUBSTRINGTERM;
            break;
        case ParseItem::ITEM_EXACTSTRINGTERM:
            sTerm = TermType::EXACTSTRINGTERM;
            break;
        case ParseItem::ITEM_SUFFIXTERM:
            sTerm = TermType::SUFFIXTERM;
            break;
        case ParseItem::ITEM_FUZZY:
            sTerm = TermType::FUZZYTERM;
            break;
        default:
            break;
        }
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
            auto qt = std::make_unique<QueryTerm>(factory.create(), ssTerm, ssIndex, sTerm);
            qt->setWeight(queryRep.GetWeight());
            qt->setUniqueId(queryRep.getUniqueId());
            if (qt->isFuzzy()) {
                qt->setFuzzyMaxEditDistance(queryRep.getFuzzyMaxEditDistance());
                qt->setFuzzyPrefixLength(queryRep.getFuzzyPrefixLength());
            }
            if (qt->encoding().isBase10Integer() ||
                ! qt->encoding().isFloat() ||
                ! factory.getRewriteFloatTerms() ||
                ! allowRewrite ||
                (ssTerm.find('.') == vespalib::string::npos))
            {
                qn = std::move(qt);
            } else {
                auto phrase = std::make_unique<PhraseQueryNode>();
                phrase->addChild(std::make_unique<QueryTerm>(factory.create(), ssTerm.substr(0, ssTerm.find('.')), ssIndex, TermType::WORD));
                phrase->addChild(std::make_unique<QueryTerm>(factory.create(), ssTerm.substr(ssTerm.find('.') + 1), ssIndex, TermType::WORD));
                auto orqn = std::make_unique<EquivQueryNode>();
                orqn->addChild(std::move(qt));
                orqn->addChild(std::move(phrase));
                qn = std::move(orqn);
            }
        }
    }
    break;
    case ParseItem::ITEM_RANK:
    {
        if (arity >= 1) {
            queryRep.next();
            qn = Build(parent, factory, queryRep, false);
            for (uint32_t skipCount = arity-1; (skipCount > 0) && queryRep.next(); skipCount--) {
                skipCount += queryRep.getArity();
            }
        }
    }
    break;
    default:
    {
        for (uint32_t skipCount = arity; (skipCount > 0) && queryRep.next(); skipCount--) {
            skipCount += queryRep.getArity();
            LOG(warning, "Does not understand anything,.... skipping %d", type);
        }
    }
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
    int32_t id = query_rep.getUniqueId();
    search::query::Weight weight = query_rep.GetWeight();
    double distance_threshold = query_rep.getDistanceThreshold();
    return std::make_unique<NearestNeighborQueryNode>(factory.create(),
                                                      query_tensor_name,
                                                      field_name,
                                                      id,
                                                      weight,
                                                      distance_threshold);
}

}
