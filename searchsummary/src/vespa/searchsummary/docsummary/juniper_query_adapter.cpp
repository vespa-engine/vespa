// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "juniper_query_adapter.h"
#include "juniper_dfw_query_item.h"
#include "juniper_dfw_term_visitor.h"
#include "keywordextractor.h"
#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/parsequery/stackdumpiterator.h>
#include <vespa/searchlib/queryeval/split_float.h>

namespace search::docsummary {

JuniperQueryAdapter::JuniperQueryAdapter(KeywordExtractor *kwExtractor, vespalib::stringref buf,
                                         const search::fef::Properties *highlightTerms)
    : _kwExtractor(kwExtractor),
      _buf(buf),
      _highlightTerms(highlightTerms)
{
}

JuniperQueryAdapter::~JuniperQueryAdapter() = default;

// TODO: put this functionality into the stack dump iterator
bool
JuniperQueryAdapter::skipItem(search::SimpleQueryStackDumpIterator *iterator) const
{
    uint32_t skipCount = iterator->getArity();

    while (skipCount > 0) {
        if (!iterator->next()) {
            return false; // stack too small
        }
        skipCount = skipCount - 1 + iterator->getArity();
    }
    return true;
}

bool
JuniperQueryAdapter::Traverse(juniper::IQueryVisitor *v) const
{
    bool rc = true;
    search::SimpleQueryStackDumpIterator iterator(_buf);
    JuniperDFWQueryItem item(&iterator);

    if (_highlightTerms->numKeys() > 0) {
        v->VisitAND(&item, 2);
    }
    while (rc && iterator.next()) {
        bool isSpecialToken = iterator.hasSpecialTokenFlag();
        switch (iterator.getType()) {
        case search::ParseItem::ITEM_OR:
        case search::ParseItem::ITEM_WEAK_AND:
        case search::ParseItem::ITEM_EQUIV:
        case search::ParseItem::ITEM_WORD_ALTERNATIVES:
            if (!v->VisitOR(&item, iterator.getArity()))
                rc = skipItem(&iterator);
            break;
        case search::ParseItem::ITEM_AND:
            if (!v->VisitAND(&item, iterator.getArity()))
                rc = skipItem(&iterator);
            break;
        case search::ParseItem::ITEM_NOT:
            if (!v->VisitANDNOT(&item, iterator.getArity()))
                rc = skipItem(&iterator);
            break;
        case search::ParseItem::ITEM_RANK:
            if (!v->VisitRANK(&item, iterator.getArity()))
                rc = skipItem(&iterator);
            break;
        case search::ParseItem::ITEM_TERM:
        case search::ParseItem::ITEM_EXACTSTRINGTERM:
        case search::ParseItem::ITEM_PURE_WEIGHTED_STRING:
            {
                vespalib::stringref term = iterator.getTerm();
                v->VisitKeyword(&item, term.data(), term.size(), false, isSpecialToken);
            }
            break;
        case search::ParseItem::ITEM_NUMTERM:
            {
                vespalib::string term = iterator.getTerm();
                queryeval::SplitFloat splitter(term);
                if (splitter.parts() > 1) {
                    if (v->VisitPHRASE(&item, splitter.parts())) {
                        for (size_t i = 0; i < splitter.parts(); ++i) {
                            v->VisitKeyword(&item,
                                    splitter.getPart(i).c_str(),
                                    splitter.getPart(i).size(), false);
                        }
                    }
                } else if (splitter.parts() == 1) {
                    v->VisitKeyword(&item,
                                    splitter.getPart(0).c_str(),
                                    splitter.getPart(0).size(), false);
                } else {
                    v->VisitKeyword(&item, term.c_str(), term.size(), false, true);
                }
            }
            break;
        case search::ParseItem::ITEM_PHRASE:
            if (!v->VisitPHRASE(&item, iterator.getArity()))
                rc = skipItem(&iterator);
            break;
        case search::ParseItem::ITEM_PREFIXTERM:
        case search::ParseItem::ITEM_SUBSTRINGTERM:
            {
                vespalib::stringref term = iterator.getTerm();
                v->VisitKeyword(&item, term.data(), term.size(), true, isSpecialToken);
            }
            break;
        case search::ParseItem::ITEM_ANY:
            if (!v->VisitANY(&item, iterator.getArity()))
                rc = skipItem(&iterator);
            break;
        case search::ParseItem::ITEM_NEAR:
            if (!v->VisitNEAR(&item, iterator.getArity(),iterator.getNearDistance()))
                rc = skipItem(&iterator);
            break;
        case search::ParseItem::ITEM_ONEAR:
            if (!v->VisitWITHIN(&item, iterator.getArity(),iterator.getNearDistance()))
                rc = skipItem(&iterator);
            break;
        case search::ParseItem::ITEM_TRUE:
        case search::ParseItem::ITEM_FALSE:
            break;
        // Unhandled items are just ignored by juniper
        case search::ParseItem::ITEM_WAND:
        case search::ParseItem::ITEM_WEIGHTED_SET:
        case search::ParseItem::ITEM_DOT_PRODUCT:
        case search::ParseItem::ITEM_PURE_WEIGHTED_LONG:
        case search::ParseItem::ITEM_SUFFIXTERM:
        case search::ParseItem::ITEM_REGEXP:
        case search::ParseItem::ITEM_PREDICATE_QUERY:
        case search::ParseItem::ITEM_SAME_ELEMENT:
        case search::ParseItem::ITEM_NEAREST_NEIGHBOR:
        case search::ParseItem::ITEM_GEO_LOCATION_TERM:
            if (!v->VisitOther(&item, iterator.getArity())) {
                rc = skipItem(&iterator);
            }
            break;
        default:
            rc = false;
        }
    }

    if (_highlightTerms->numKeys() > 1) {
        v->VisitAND(&item, _highlightTerms->numKeys());
    }
    JuniperDFWTermVisitor tv(v);
    _highlightTerms->visitProperties(tv);

    return rc;
}

bool
JuniperQueryAdapter::UsefulIndex(const juniper::QueryItem* item) const
{
    if (_kwExtractor == nullptr) {
        return true;
    }
    auto index = item->get_index();
    return _kwExtractor->isLegalIndex(index);
}

}
