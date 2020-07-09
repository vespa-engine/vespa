// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "query.h"
#include <vespa/searchlib/parsequery/stackdumpiterator.h>

#include <vespa/log/log.h>
LOG_SETUP(".vsm.querynode");

namespace search::streaming {

namespace {
    vespalib::stringref DEFAULT("default");
    bool isPhraseOrNear(const QueryNode * qn) {
        return dynamic_cast<const NearQueryNode *> (qn) || dynamic_cast<const PhraseQueryNode *> (qn);
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
                    UP child = Build(qc, factory, queryRep, allowRewrite && !isPhraseOrNear(qn.get()));
                    qc->push_back(std::move(child));
                }
            }
        }
    }
    break;
    case ParseItem::ITEM_NUMTERM:
    case ParseItem::ITEM_LOCATION_TERM:
    case ParseItem::ITEM_TERM:
    case ParseItem::ITEM_PREFIXTERM:
    case ParseItem::ITEM_REGEXP:
    case ParseItem::ITEM_SUBSTRINGTERM:
    case ParseItem::ITEM_EXACTSTRINGTERM:
    case ParseItem::ITEM_SUFFIXTERM:
    case ParseItem::ITEM_PURE_WEIGHTED_STRING:
    case ParseItem::ITEM_PURE_WEIGHTED_LONG:
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
        vespalib::stringref term = queryRep.getTerm();
        QueryTerm::SearchTerm sTerm(QueryTerm::WORD);
        switch (type) {
        case ParseItem::ITEM_REGEXP:
            sTerm = QueryTerm::REGEXP;
            break;
        case ParseItem::ITEM_PREFIXTERM:
            sTerm = QueryTerm::PREFIXTERM;
            break;
        case ParseItem::ITEM_SUBSTRINGTERM:
            sTerm = QueryTerm::SUBSTRINGTERM;
            break;
        case ParseItem::ITEM_EXACTSTRINGTERM:
            sTerm = QueryTerm::EXACTSTRINGTERM;
            break;
        case ParseItem::ITEM_SUFFIXTERM:
            sTerm = QueryTerm::SUFFIXTERM;
            break;
        default:
            break;
        }
        QueryTerm::string ssTerm(term);
        QueryTerm::string ssIndex(index);
        if (ssIndex == "sddocname") {
            // This is suboptimal as the term should be checked too.
            // But it will do for now as only correct sddocname queries are sent down.
            qn.reset(new TrueNode());
        } else {
            std::unique_ptr<QueryTerm> qt(new QueryTerm(factory.create(), ssTerm, ssIndex, sTerm));
            qt->setWeight(queryRep.GetWeight());
            qt->setUniqueId(queryRep.getUniqueId());
            if ( qt->encoding().isBase10Integer() || ! qt->encoding().isFloat() || ! factory.getRewriteFloatTerms() || !allowRewrite || (ssTerm.find('.') == vespalib::string::npos)) {
                qn = std::move(qt);
            } else {
                std::unique_ptr<PhraseQueryNode> phrase(new PhraseQueryNode());
                phrase->push_back(UP(new QueryTerm(factory.create(), ssTerm.substr(0, ssTerm.find('.')), ssIndex, QueryTerm::WORD)));
                phrase->push_back(UP(new QueryTerm(factory.create(), ssTerm.substr(ssTerm.find('.') + 1), ssIndex, QueryTerm::WORD)));
                std::unique_ptr<EquivQueryNode> orqn(new EquivQueryNode());
                orqn->push_back(std::move(qt));
                orqn->push_back(std::move(phrase));
                qn.reset(orqn.release());
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

}
