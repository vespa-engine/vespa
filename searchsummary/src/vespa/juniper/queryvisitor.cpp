// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "queryvisitor.h"
#include "Matcher.h"
#include "juniperdebug.h"
#include "query.h"
#include "query_item.h"
#include "queryhandle.h"

#include <vespa/log/log.h>
LOG_SETUP(".juniper.queryvisitor");

/* Implementation of the query visit interface between Juniper and the query provider */
/* Implementation note: insert() must be called for all calls in the tree to
 * keep the input in sync with the stack traversal. insert will call AddChild
 * which in the cases of NULL as input will update the arity accordingly.
 * Any zero children nodes as result of this will be eliminated by the simplifier.
 */

void QueryVisitor::insert(QueryExpr* expr) {
    if (_current)
        _current = _current->AddChild(expr);
    else {
        // Just a sanity check that there are no overflow stack elements
        if (_got_stack && expr) {
            if (_query && _query->StackComplete()) { LOG(warning, "juniper: Overflow stack element discarded"); }
            delete expr;
            return;
        }
        if (expr) {
            _current = _query = expr;
            _got_stack = true;
        }
    }
}

QueryVisitor::QueryVisitor(const IQuery& fquery, QueryHandle* qhandle)
  : _fquery(&fquery),
    _query(nullptr),
    _current(nullptr),
    _qhandle(qhandle),
    _term_index(0),
    _got_stack(false) {
    /* Create a query node structure by traversing the input */
    bool ok_stack = fquery.Traverse(this);

    if (ok_stack) {
        /* Do necessary post processing on the query structure to ensure that all nodes have
         * complete information:
         */
        postprocess_query();
    } else if (_query) {
        delete _query;
        _query = nullptr;
    }
    _fquery = nullptr; // To avoid misuse after invalidation..
}

QueryVisitor::~QueryVisitor() {
    delete _query;
}

void QueryVisitor::postprocess_query() {
    if (LOG_WOULD_LOG(debug)) {
        if (_query) {
            std::string s;
            _query->Dump(s);
            LOG(debug, "juniper input stack: %s", s.c_str());
        } else {
            LOG(debug, "juniper input stack: No stack found!");
        }
    }
    SimplifyStack(_query);
    if (!_query) return;
    // convert special case of one query term to a node with 1 child:
    if (_query->_arity == 0) {
        auto* newroot = new QueryNode(1, _query->_weight, _query->_weight);
        newroot->AddChild(_query);
        _query = newroot;
    }
    // Handle limit in root node only for now..
    if (!_current && _qhandle->_options & X_LIMIT) {
        QueryNode* qn = _query->AsNode();
        if (qn) qn->_limit = _qhandle->_limit;
    }
    _query->ComputeThreshold();
}

QueryExpr* QueryVisitor::GetQuery() {
    QueryExpr* q = _query;
    _query = nullptr;
    return q;
}

bool QueryVisitor::VisitAND(const QueryItem*, int arity) {
    LOG(debug, "juniper: VisitAND[%d]", arity);
    auto* node = new QueryNode(arity, -1);
    node->_options = _qhandle->_options | X_AND;
    insert(node);
    return true;
}

bool QueryVisitor::VisitOR(const QueryItem*, int arity) {
    LOG(debug, "juniper: VisitOR[%d]", arity);
    auto* node = new QueryNode(arity, -1);
    node->_options = _qhandle->_options | X_OR;
    insert(node);
    return true;
}

bool QueryVisitor::VisitANY(const QueryItem*, int arity) {
    LOG(debug, "juniper: VisitANY[%d]", arity);
    auto* node = new QueryNode(arity, -1);
    node->_options = _qhandle->_options | X_ANY;
    insert(node);
    return true;
}

bool QueryVisitor::VisitNEAR(const QueryItem*, int arity, int limit) {
    LOG(debug, "juniper: VisitNEAR(%d)[%d]", limit, arity);
    auto* node = new QueryNode(arity, -1);
    node->_options = _qhandle->_options | X_AND | X_LIMIT | X_COMPLETE | X_CONSTR | X_CHKVAL;
    node->_limit = limit;
    insert(node);
    return true;
}

bool QueryVisitor::VisitWITHIN(const QueryItem*, int arity, int limit) {
    LOG(debug, "juniper: VisitWITHIN(%d)[%d]", limit, arity);
    auto* node = new QueryNode(arity, -1);
    node->_options = _qhandle->_options | X_AND | X_LIMIT | X_ORDERED | X_COMPLETE | X_CONSTR | X_CHKVAL;
    node->_limit = limit;
    insert(node);
    return true;
}

bool QueryVisitor::VisitRANK(const QueryItem*, int arity) {
    LOG(debug, "juniper: VisitRANK[%d]", arity);
    auto* node = new QueryNode(arity, -1);
    node->_options = X_ONLY_1; // Only keep first child (simpl.executed by simplifier)
    insert(node);
    return true;
}

bool QueryVisitor::VisitPHRASE(const QueryItem*, int arity) {
    LOG(debug, "juniper: VisitPHRASE[%d]", arity);
    // PHRASE is identical to WITHIN(0) + exact matches only
    auto* node = new QueryNode(arity, -1);
    node->_options = _qhandle->_options | X_AND | X_LIMIT | X_ORDERED | X_COMPLETE | X_EXACT | X_CHKVAL;
    node->_limit = 0;
    insert(node);
    return true;
}

bool QueryVisitor::VisitANDNOT(const QueryItem*, int arity) {
    LOG(debug, "juniper: VisitANDNOT[%d]", arity);
    auto* node = new QueryNode(arity, -1);
    node->_options = X_ONLY_1; // Only keep first child (simpl.executed by simplifier)
    insert(node);
    return true;
}

bool QueryVisitor::VisitOther(const QueryItem*, int arity) {
    LOG(debug, "juniper: VisitOther[%d]", arity);
    insert(nullptr);
    return false;
}

std::string_view QueryVisitor::get_index(const QueryItem* item) {
    return item->get_index();
}

void QueryVisitor::visitKeyword(const QueryItem* item, std::string_view keyword, bool prefix, bool specialToken) {
    if (keyword.empty()) {
        // Do not consider empty terms.
        insert(nullptr); // keep arity of parent in sync!
        return;
    }
    juniper::ItemCreator creator = item->get_creator();
    switch (creator) {
    case juniper::ItemCreator::CREA_ORIG:
        LOG(debug, "(juniper::VisitKeyword) Found valid creator '%s'", juniper::creator_text(creator));
        break;
    default:
        /** Keep track of eliminated children to have correct arity in rep. */
        insert(nullptr);
        if (LOG_WOULD_LOG(debug)) {
            std::string s(keyword);
            std::string ind(get_index(item));
            LOG(debug, "juniper: VisitKeyword(%s:%s) - skip - unwanted creator %s", ind.c_str(), s.c_str(),
                juniper::creator_text(creator));
        }
        return;
    }

    if (!_fquery->UsefulIndex(item)) {
        if (LOG_WOULD_LOG(debug)) {
            std::string s(keyword);
            std::string ind(get_index(item));
            LOG(debug, "juniper: VisitKeyword(%s:%s) - not applicable index", ind.c_str(), s.c_str());
        }
        insert(nullptr); // keep arity of parent in sync!
        return;
    }
    if (LOG_WOULD_LOG(debug)) {
        std::string s(keyword);
        std::string ind(get_index(item));
        LOG(debug, "juniper: VisitKeyword(%s%s%s)", ind.c_str(), (!ind.empty() ? ":" : ""), s.c_str());
    }

    auto* term = new QueryTerm(keyword, _term_index++, item->get_weight());
    if (prefix) {
        bool is_wild =
            std::any_of(keyword.begin(), keyword.end(), [](char c) { return (c == '*') || (c == '?'); });
        term->_options |= (is_wild ? X_WILD : X_PREFIX);
    }
    if (specialToken) { term->_options |= X_SPECIALTOKEN; }
    insert(term);
}

namespace juniper {

const char* creator_text(ItemCreator creator) {
    switch (creator) {
    case ItemCreator::CREA_ORIG:   return "CREA_ORIG";
    case ItemCreator::CREA_FILTER: return "CREA_FILTER";
    default:                       return "(unknown creator)";
    }
}

} // end namespace juniper
