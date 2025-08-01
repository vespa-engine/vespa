// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "matchobject.h"
#include "charutil.h"
#include "juniper_separators.h"
#include "result.h"
#include "wildcard_match.h"
#include <stack>
#include <vespa/log/log.h>
LOG_SETUP(".juniper.matchobject");

using namespace juniper::separators;

class traverser : public IQueryExprVisitor {
public:
    explicit traverser(MatchObject& mo) noexcept : _mo(mo) {}

    void VisitQueryNode(QueryNode*) override {
        // We must not add this node to nonterminals before all children has been added!
        // Matcher::flush_candidates() depend on this order to avoid having to loop
        // until no more candidates...
    }

    void RevisitQueryNode(QueryNode* n) override { _mo.add_nonterm(n); }

    void VisitQueryTerm(QueryTerm* t) override { _mo.add_queryterm(t); }

private:
    MatchObject& _mo;
};

class query_expander : public IQueryExprVisitor {
public:
    query_expander(MatchObject& mo) noexcept : _caller(), _mo(mo) {}

    void VisitQueryTerm(QueryTerm* orig) override {
        QueryTerm* t = new QueryTerm(orig); // just clone term
        _mo.add_queryterm(t);
        update(t);
    }

    // Visit on descent:
    void VisitQueryNode(QueryNode* n) override {
        QueryNode* qn = new QueryNode(n);
        update(qn);
        _caller.push(qn);
    }

    // revisit on return:
    void RevisitQueryNode(QueryNode* n) override {
        QueryNode* qn = _caller.top();
        if (n->_parent) _caller.pop();
        _mo.add_nonterm(qn);
    }

    QueryExpr* NewQuery() {
        if (_caller.empty()) return nullptr;
        return _caller.top();
    }

private:
    void update(QueryExpr* e) {
        if (!_caller.empty()) _caller.top()->AddChild(e);
    }

    std::stack<QueryNode*> _caller; // Recursion emulator..
    MatchObject&           _mo;
}; // class query_expander

MatchObject::MatchObject(QueryExpr* query)
  : _query(query),
    _qt(),
    _nonterms(),
    _match_overlap(false),
    _max_arity(0),
    _qt_byname() {
    LOG(debug, "MatchObject(default)");
    traverser tr(*this);
    query->Accept(tr); // Initialize structure for the query
    _max_arity = query->MaxArity();
}

MatchObject::~MatchObject() {
    // _query is now always owned by the match object!
    delete _query;
}

bool MatchObject::Match(MatchObject::iterator& mi, Token& token, unsigned& options) {
    QueryTerm* q = mi.first_match(token);
    if (!q) return false;
    options = 0;
    q->total_match_cnt++;
    if (q->ucs4_len == static_cast<size_t>(token.curlen)) {
        options |= X_EXACT;
        q->exact_match_cnt++;
    }
    return true;
}

void MatchObject::add_nonterm(QueryNode* n) {
    _nonterms.push_back(n);
    n->_node_idx = _nonterms.size() - 1;
}

void MatchObject::add_queryterm(QueryTerm* nt) {
    _qt.push_back(nt);
    nt->idx = _qt.size() - 1;

    _qt_byname.Insert(*(reinterpret_cast<const queryterm_hashtable::keytype*>(nt->ucs4_term())), nt);

    LOG(debug, "MatchObject: adding term '%s'", nt->term());
}

match_iterator::match_iterator(MatchObject* mo, Result* rhandle)
  : _table(mo->_qt_byname),
    _el(nullptr),
    _rhandle(rhandle),
    _reductions(mo->HasReductions()),
    _reduce_matches(nullptr),
    _reduce_matches_it(),
    _mo(mo),
    _len(0),
    _stem_min(rhandle->StemMin()),
    _stemext(rhandle->StemExt()),
    _term(nullptr) {}

QueryTerm* match_iterator::first() {
    for (; _el != nullptr; _el = _el->GetNext()) {
        QueryTerm* q = _el->GetItem();

        // If exact match is desired by this subexpression,
        // only have effect if exact match
        if (q->Exact() && _len > q->len()) continue;

        if (q->is_wildcard()) {
            if (fast::util::wildcard_match(_term, q->ucs4_term()) == false) continue;
            return q;
        }

        if (_len < q->ucs4_len) continue;
        // allow prefix match iff prefix query term or
        // rest < _stem_extend and length > stem_min
        if (!q->is_prefix()) {
            size_t stem_extend = (q->ucs4_len <= _stem_min ? 0 : _stemext);
            if (_len > q->ucs4_len + stem_extend) continue;
        }
        if (juniper::strncmp(_term, q->ucs4_term(), q->ucs4_len) != 0) continue;
        return q;
    }
    return nullptr;
}

QueryTerm* match_iterator::next_reduce_match() {
    if (!_reduce_matches) return nullptr;
    if (_reduce_matches_it != _reduce_matches->end()) {
        QueryTerm* t = *_reduce_matches_it;
        ++_reduce_matches_it;
        return t;
    }
    delete _reduce_matches;
    _reduce_matches = nullptr;
    return nullptr;
}

QueryTerm* match_iterator::first_match(Token& token) {
    const ucs4_t* term = token.token;
    size_t        len = token.curlen;

    // Check for interlinear annotation, and "lie" to the matchobject
    if (static_cast<char32_t>(*term) == interlinear_annotation_anchor) {
        const ucs4_t* terminator = term + len;
        token.token = ++term;
        // starting annotation, skip to after SEPARATOR
        while (term < terminator && static_cast<char32_t>(*term) != interlinear_annotation_separator) { term++; }
        const ucs4_t* separator = term;
        // found separator, assume terminator at end
        if (term + 2 < terminator) {
            token.token = ++term; // skip the SEPARATOR
            QueryTerm* qt;
            // process until TERMINATOR is found
            while (term < terminator && static_cast<char32_t>(*term) != interlinear_annotation_terminator) {
                // Handle multiple terms in the same annotation, for compound nouns or multiple stems
                if (*term == ' ' || static_cast<char32_t>(*term) == interlinear_annotation_separator) {
                    token.curlen = term - token.token;
                    LOG(debug, "recurse A to match token %u..%u len %d", token.token[0],
                        token.token[token.curlen - 1], token.curlen);
                    qt = this->first_match(token);
                    if (qt != nullptr) { return qt; }
                    token.token = ++term; // skip SPACE
                } else {
                    ++term;
                }
            }
            token.curlen = term - token.token;
            LOG(debug, "recurse B to match token %u..%u len %d", token.token[0], token.token[token.curlen - 1],
                token.curlen);
            return this->first_match(token);
        } else {
            // broken annotation
            // process first part (before SEPARATOR) instead
            token.curlen = separator - token.token;
            LOG(debug, "recurse C to match token %u..%u len %d", token.token[0], token.token[token.curlen - 1],
                token.curlen);
            return this->first_match(token);
        }
    } else {
        // plain token, so just reference the term
        _term = token.token;
    }

    queryterm_hashtable::keytype termval = *(reinterpret_cast<const queryterm_hashtable::keytype*>(term));
    queryterm_hashtable::keytype keyval = termval;
    if (LOG_WOULD_LOG(spam)) {
        char utf8term[1024];
        Fast_UnicodeUtil::utf8ncopy(utf8term, term, 1024, len);
        LOG(spam, "term %s, len %ld, keyval 0x%x termval 0x%x", utf8term, len, keyval, termval);
    }
    _el = _table.FindRef(keyval);
    _len = len;
    QueryTerm* rtrn = first();

    if (rtrn == nullptr) {
        _el = _table.FindRef('*');
        if ((rtrn = first()) == nullptr) {
            _el = _table.FindRef('?');
            rtrn = first();
        }
    }
    return rtrn;
}

/** Return the current element without advancing iterator pointers */
QueryTerm* match_iterator::current() {
    if (_el) return _el->GetItem();
    if (!_reduce_matches) return nullptr;
    if (_reduce_matches_it != _reduce_matches->end()) {
        QueryTerm* t = *_reduce_matches_it;
        return t;
    }
    delete _reduce_matches;
    return nullptr;
}

QueryTerm* match_iterator::next() {
    if (_el) {
        _el = _el->GetNext();
        return first();
    } else if (_reduce_matches)
        return next_reduce_match();
    return nullptr;
}
