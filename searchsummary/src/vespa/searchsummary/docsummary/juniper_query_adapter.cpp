// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "juniper_query_adapter.h"
#include "i_query_term_filter.h"
#include "juniper_dfw_query_item.h"
#include "juniper_dfw_term_visitor.h"
#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/parsequery/stackdumpiterator.h>
#include <vespa/searchlib/queryeval/split_float.h>
#include <vespa/searchlib/query/query_normalization.h>

namespace search::docsummary {

JuniperQueryAdapter::JuniperQueryAdapter(const QueryNormalization * normalization, const IQueryTermFilter *query_term_filter,
                                         std::string_view buf, const search::fef::Properties & highlightTerms)
    : _query_normalization(normalization),
      _query_term_filter(query_term_filter),
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

    if (_highlightTerms.numKeys() > 0) {
        v->VisitAND(&item, 2);
    }
    while (rc && iterator.next()) {
        bool isSpecialToken = iterator.hasSpecialTokenFlag();
        bool prefix_like = false;
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
        case search::ParseItem::ITEM_PREFIXTERM:
        case search::ParseItem::ITEM_SUBSTRINGTERM:
            prefix_like = true;
            [[fallthrough]];
        case search::ParseItem::ITEM_TERM:
        case search::ParseItem::ITEM_EXACTSTRINGTERM:
        case search::ParseItem::ITEM_PURE_WEIGHTED_STRING:
            {
                vespalib::string term = iterator.getTerm();
                if (_query_normalization) {
                    vespalib::string index = iterator.getIndexName();
                    if (index.empty()) {
                        index = SimpleQueryStackDumpIterator::DEFAULT_INDEX;
                    }
                    Normalizing normalization = _query_normalization->normalizing_mode(index);
                    TermType termType = ParseItem::toTermType(iterator.getType());
                    v->visitKeyword(&item, QueryNormalization::optional_fold(term, termType, normalization),
                                    prefix_like, isSpecialToken);
                } else {
                    v->visitKeyword(&item, term, prefix_like, isSpecialToken);
                }
            }
            break;
        case search::ParseItem::ITEM_NUMTERM:
            {
                vespalib::string term = iterator.getTerm();
                queryeval::SplitFloat splitter(term);
                if (splitter.parts() > 1) {
                    if (v->VisitPHRASE(&item, splitter.parts())) {
                        for (size_t i = 0; i < splitter.parts(); ++i) {
                            v->visitKeyword(&item, splitter.getPart(i), false, false);
                        }
                    }
                } else if (splitter.parts() == 1) {
                    v->visitKeyword(&item, splitter.getPart(0), false, false);
                } else {
                    v->visitKeyword(&item, term, false, true);
                }
            }
            break;
        case search::ParseItem::ITEM_PHRASE:
            if (!v->VisitPHRASE(&item, iterator.getArity()))
                rc = skipItem(&iterator);
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
            if (!v->VisitOther(&item, iterator.getArity())) {
                rc = skipItem(&iterator);
            }
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
        case search::ParseItem::ITEM_FUZZY:
        case search::ParseItem::ITEM_STRING_IN:
        case search::ParseItem::ITEM_NUMERIC_IN:
            if (!v->VisitOther(&item, iterator.getArity())) {
                rc = skipItem(&iterator);
            }
            break;
        default:
            rc = false;
        }
    }

    if (_highlightTerms.numKeys() > 1) {
        v->VisitAND(&item, _highlightTerms.numKeys());
    }
    JuniperDFWTermVisitor tv(v);
    _highlightTerms.visitProperties(tv);

    return rc;
}

bool
JuniperQueryAdapter::UsefulIndex(const juniper::QueryItem* item) const
{
    if (_query_term_filter == nullptr) {
        return true;
    }
    auto index = item->get_index();
    return _query_term_filter->use_view(index);
}

}
