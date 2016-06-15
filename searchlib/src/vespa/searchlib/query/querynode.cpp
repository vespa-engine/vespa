// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/searchlib/query/query.h>
#include <vespa/log/log.h>
LOG_SETUP(".vsm.querynode");

namespace search
{

IMPLEMENT_IDENTIFIABLE_ABSTRACT_NS(search, QueryNode, vespalib::Identifiable);

void TestClose(QueryNode::LP & qn, QueryNodeList & currentNodeList)
{
    if (&qn != NULL) {
        QueryConnector * qc = dynamic_cast<QueryConnector *> (qn.get());
        if (qc) {
            // qc->QueryNodeList = currentNodeList;
            for (size_t i=0; i < currentNodeList.size(); i++) {
                qc->push_back(currentNodeList[i]);
            }
            currentNodeList.clear();
            currentNodeList.push_back(qn);
        }
    }
}

void NewNode(QueryNode::LP & qn, QueryNodeList & currentNodeList, size_t count=0)
{
    if ( ! currentNodeList.empty() ) {
        QueryConnector *qc = dynamic_cast<QueryConnector *> (&*currentNodeList.back());
        if (qc != 0 &&
            ((dynamic_cast<NotQueryNode *>(qc) != NULL) ||
             (dynamic_cast<PhraseQueryNode *>(qc) != NULL && (count == qc->size()))))
        {
            qc->push_back(qn);
        } else {
            currentNodeList.push_back(qn);
        }
    } else {
        currentNodeList.push_back(qn);
    }
}

namespace {
    vespalib::stringref DEFAULT("default");
}

#define CASE(c, q) case c: { qn.reset(new q()); } break;
QueryNode::UP QueryNode::Build(const QueryNode * parent, const QueryNodeResultBase & org, search::SimpleQueryStackDumpIterator & queryRep, bool allowRewrite)
{
    unsigned int arity = queryRep.getArity();
    search::ParseItem::ItemType type = queryRep.getType();
    UP qn;
    switch (type) {
    case search::ParseItem::ITEM_AND:
    case search::ParseItem::ITEM_OR:
    case search::ParseItem::ITEM_WEAK_AND:
    case search::ParseItem::ITEM_EQUIV:
    case search::ParseItem::ITEM_WEIGHTED_SET:
    case search::ParseItem::ITEM_DOT_PRODUCT:
    case search::ParseItem::ITEM_WAND:
    case search::ParseItem::ITEM_NOT:
    case search::ParseItem::ITEM_PHRASE:
    case search::ParseItem::ITEM_NEAR:
    case search::ParseItem::ITEM_ONEAR:
    {
        qn.reset(QueryConnector::create(type));
        if (qn.get()) {
            QueryConnector * qc = dynamic_cast<QueryConnector *> (qn.get());
            NearQueryNode * nqn = dynamic_cast<NearQueryNode *> (qc);
            if (nqn) {
                nqn->distance(queryRep.getArg1());
            }
            if ((type == search::ParseItem::ITEM_WEAK_AND) ||
                (type == search::ParseItem::ITEM_WEIGHTED_SET) ||
                (type == search::ParseItem::ITEM_DOT_PRODUCT) ||
                (type == search::ParseItem::ITEM_WAND))
            {
                const char * index;
                size_t indexLen(0);
                queryRep.getIndexName(&index, &indexLen);
                qn->setIndex(vespalib::string(index, indexLen));
            }
            for (size_t i=0; i < arity; i++) {
                queryRep.next();
                if (qc->isFlattenable(queryRep.getType())) {
                    arity += queryRep.getArity();
                } else {
                    LP child(Build(qc,
                                   org,
                                   queryRep,
                                   allowRewrite && ((dynamic_cast<NearQueryNode *> (qn.get()) == NULL) && (dynamic_cast<PhraseQueryNode *> (qn.get()) == NULL))).release());
                    qc->push_back(child);
                }
            }
        }
    }
    break;
    case search::ParseItem::ITEM_NUMTERM:
    case search::ParseItem::ITEM_TERM:
    case search::ParseItem::ITEM_PREFIXTERM:
    case search::ParseItem::ITEM_REGEXP:
    case search::ParseItem::ITEM_SUBSTRINGTERM:
    case search::ParseItem::ITEM_EXACTSTRINGTERM:
    case search::ParseItem::ITEM_SUFFIXTERM:
    case search::ParseItem::ITEM_PURE_WEIGHTED_STRING:
    case search::ParseItem::ITEM_PURE_WEIGHTED_LONG:
    {
        const char * index;
        size_t indexLen(0);
        queryRep.getIndexName(&index, &indexLen);
        if (indexLen == 0) {
            if ((type == search::ParseItem::ITEM_PURE_WEIGHTED_STRING) || (type == search::ParseItem::ITEM_PURE_WEIGHTED_LONG)) {
                const vespalib::string & ref = parent->getIndex();
                index = ref.c_str();
                indexLen = ref.size();
            } else {
                index = "default";
                indexLen = strlen(index);
            }
        }
        const char * term;
        size_t termLen(0);
        queryRep.getTerm(&term, &termLen);
        QueryTerm::SearchTerm sTerm(QueryTerm::WORD);
        switch (type) {
        case search::ParseItem::ITEM_REGEXP:
            sTerm = QueryTerm::REGEXP;
            break;
        case search::ParseItem::ITEM_PREFIXTERM:
            sTerm = QueryTerm::PREFIXTERM;
            break;
        case search::ParseItem::ITEM_SUBSTRINGTERM:
            sTerm = QueryTerm::SUBSTRINGTERM;
            break;
        case search::ParseItem::ITEM_EXACTSTRINGTERM:
            sTerm = QueryTerm::EXACTSTRINGTERM;
            break;
        case search::ParseItem::ITEM_SUFFIXTERM:
            sTerm = QueryTerm::SUFFIXTERM;
            break;
        default:
            break;
        }
        QueryTerm::string ssTerm(term, termLen);
        QueryTerm::string ssIndex(index, indexLen);
        if (ssIndex == "sddocname") {
            // This is suboptimal as the term should be checked too.
            // But it will do for now as only correct sddocname queries are sent down.
            qn.reset(new TrueNode());
        } else {
            std::unique_ptr<QueryTerm> qt(new QueryTerm(org, ssTerm, ssIndex, sTerm));
            qt->setWeight(queryRep.GetWeight());
            qt->setUniqueId(queryRep.getUniqueId());
            if ( qt->encoding().isBase10Integer() || ! qt->encoding().isFloat() || ! org.getRewriteFloatTerms() || !allowRewrite || (ssTerm.find('.') == vespalib::string::npos)) {
                qn.reset(qt.release());
            } else {
                std::unique_ptr<PhraseQueryNode> phrase(new PhraseQueryNode());

                phrase->push_back(LP(new QueryTerm(org, ssTerm.substr(0, ssTerm.find('.')), ssIndex, QueryTerm::WORD)));
                phrase->push_back(LP(new QueryTerm(org, ssTerm.substr(ssTerm.find('.') + 1), ssIndex, QueryTerm::WORD)));
                std::unique_ptr<EquivQueryNode> orqn(new EquivQueryNode());
                orqn->push_back(LP(qt.release()));
                orqn->push_back(LP(phrase.release()));
                qn.reset(orqn.release());
            }
        }
    }
    break;
    case search::ParseItem::ITEM_RANK:
    {
        if (arity >= 1) {
            queryRep.next();
            qn = Build(parent, org, queryRep, false);
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
#undef CASE

const HitList & QueryNode::evaluateHits(HitList & hl) const
{
    return hl;
}

}
