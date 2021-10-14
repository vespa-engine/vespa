// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "querynode.h"
#include "queryvisitor.h"
#include "juniperdebug.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".juniper.querynode");

/** Implementation of the internal query data structure used by the matching engine
 *  in Matcher.h
 */

QueryExpr::QueryExpr(int weight, int arity) :
    _options(0), _weight(weight), _arity(arity), _parent(NULL), _childno(0)
{ }

QueryExpr::QueryExpr(QueryExpr* e) :
    _options(e->_options),
    _weight(e->_weight),
    _arity(e->_arity),
    _parent(NULL),
    _childno(0)
{ }

QueryExpr::~QueryExpr() { }


QueryTerm::QueryTerm(const char* t, int length, int ix, int wgt)
    : QueryExpr(wgt, 0), len(length),
      ucs4_len(0),
      total_match_cnt(0), exact_match_cnt(0),
      idx(ix), rewriter(NULL), reduce_matcher(NULL), _rep(NULL),
      _ucs4_term(NULL)
{
    if (len <= 0)
        len = strlen(t);
    _rep = new char[len+1];
    strncpy(_rep, t, len);                _rep[len] = '\0';
    _ucs4_term = new ucs4_t[len+1];
    Fast_UnicodeUtil::ucs4copy(_ucs4_term, _rep);
    ucs4_len = Fast_UnicodeUtil::ucs4strlen(_ucs4_term);
}


QueryTerm::QueryTerm(QueryTerm* t)
    : QueryExpr(t), len(t->len),
      ucs4_len(0),
      total_match_cnt(0), exact_match_cnt(0),
      idx(-1), rewriter(NULL), reduce_matcher(NULL), _rep(NULL),
      _ucs4_term(NULL)
{
    _rep = new char[len+1];
    strncpy(_rep, t->term(), len);         _rep[len] = '\0';
    _ucs4_term = new ucs4_t[len+1];
    Fast_UnicodeUtil::ucs4copy(_ucs4_term, _rep);
    ucs4_len = Fast_UnicodeUtil::ucs4strlen(_ucs4_term);
}



QueryTerm::~QueryTerm()
{
    delete[] _rep;
    delete[] _ucs4_term;
}


QueryNode::QueryNode(int arity, int threshold, int weight) :
    QueryExpr(weight, arity), _threshold(threshold), _limit(0),
    _children(NULL),
    _nchild(0), _node_idx(-1)
{
    assert(arity > 0);
    _children = new QueryExpr*[arity];
}


QueryNode::QueryNode(QueryNode* n)
    : QueryExpr(n),
      _threshold(n->_threshold),
      _limit(n->_limit),
      _children(NULL),
      _nchild(0),
      _node_idx(n->_node_idx)
{
    _children = new QueryExpr*[_arity];
}



QueryNode::~QueryNode()
{
    for (int i = 0; i < _nchild; i++)
        delete _children[i];
    delete[] _children;
    _nchild = 0;
}


int QueryNode::Limit() { return _options & X_LIMIT ? _limit : -1; }
int QueryTerm::Limit() { return 0; }


QueryNode* QueryTerm::AddChild(QueryExpr*)
{
    LOG(warning, "stack inconsistency, attempt to add children to a terminal node");

    QueryNode* node = _parent;
    while (node && node->Complete()) node = node->_parent;
    return node;
}


QueryNode* QueryNode::AddChild(QueryExpr* child)
{
    if (!child)
        _arity--;
    else
    {
        child->_parent = this;
        child->_childno = _nchild;
        _children[_nchild++] = child;
        if (child->_arity > 0) // we know this is a QueryNode from the arity info
            return static_cast<QueryNode*>(child);
    }
    QueryNode* node = this;
    while (node && node->Complete()) node = node->_parent;
    return node;
}


void QueryExpr::ComputeThreshold() {}


// Compute threshold and constraint info

void QueryNode::ComputeThreshold()
{
    bool no_threshold = false;
    int th = 0;
    if (_options & (X_OR|X_ANY))
        th = 0xfffffff;
    else if (!(_options & X_AND))
        no_threshold = true;

    for (int i = 0; i < _nchild; i++) {
        QueryExpr* qe = _children[i];
        qe->ComputeThreshold();
        if (!no_threshold) {
            int w = qe->_weight;
            if (_options | X_AND) th += w;
            else
                th = std::min(th, w);
        }
        // Propagate any X_CONSTR and X_CHKVAL bit upwards
        _options |= (qe->_options & (X_CONSTR | X_CHKVAL));
    }
    if ((!no_threshold) && _threshold < 0)
        _threshold = th;
}


void QueryTerm::Dump(std::string& out)
{
    out.append(term());
    out.append(vespalib::make_string("%s:%d", (_options & X_PREFIX ? "*" : ""), _weight));
}


void QueryNode::Dump(std::string& out)
{
    out.append(vespalib::make_string("Node<a:%d", _arity));
    if (_options & X_ORDERED) out.append(",o");
    if (_options & X_NOT) out.append("!");
    if (_options & X_LIMIT)
        out.append(vespalib::make_string(",l:%d", _limit));
    if (_options & X_EXACT) out.append(",e");
    if (_options & X_CHKVAL) out.append(",v");
    else if (_options & X_CONSTR) out.append(",z");
    if (_options & X_COMPLETE) out.append(",c");
    out.append(">[");
    for (int i = 0; i < _nchild; i++)
    {
        if (i < _nchild && i > 0) out.append(",");
        _children[i]->Dump(out);
    }
    out.append("]");
}


bool QueryNode::StackComplete()
{
    // Stack is complete if rightmost nodes in tree are complete
    return (Complete() && (!_arity || _children[_arity - 1]->StackComplete()));
}


bool QueryTerm::StackComplete()
{
    return true;
}


QueryNode* QueryNode::AsNode()
{
    return this;
}

QueryNode* QueryTerm::AsNode()
{
    return NULL;
}

QueryTerm* QueryNode::AsTerm()
{
    return NULL;
}

QueryTerm* QueryTerm::AsTerm()
{
    return this;
}

bool QueryTerm::Complex()
{
    return false;
}

bool QueryNode::Complex()
{
    for (int i = 0; i < _nchild; i++) {
        if (_children[i]->_arity > 1) return true;
    }
    return false;
}


int QueryNode::MaxArity()
{
    int max_arity = _arity;
    for (int i = 0; i < _nchild; i++) {
        int ma = _children[i]->MaxArity();
        if (ma > max_arity) max_arity = ma;
    }
    return max_arity;
}


bool QueryNode::AcceptsInitially(QueryExpr* n)
{
    assert(n->_parent == this);
//  return (!(_options & X_ORDERED)) || n->_childno == 0;
    // currently implicitly add all terms even for ordered..
    (void) n;
    return true;
}


/** Modify the given stack by eliminating unnecessary internal nodes
 *  with arity 1 or non-terms with arity 0
 */
void SimplifyStack(QueryExpr*& orig_stack)
{
    if (!orig_stack) return;
    QueryNode* node = orig_stack->AsNode();
    if (!node) return; // Leaf node - no simplifications possible

    int compact = 0;
    int i;
    if (!node->Complete()) {
        LOG(warning, "juniper: query stack incomplete, got arity %d, expected %d",
            node->_nchild, node->_arity);
        delete node;
        orig_stack = NULL;
        return;
    }

    for (i = 0; i < node->_arity; i++) {
        if (i > 0 && (node->_options & X_ONLY_1)) {
            // Get rid of children # >2 for RANK/ANDNOT
            delete node->_children[i];
            node->_children[i] = NULL;
        }
        else
            SimplifyStack(node->_children[i]);

        if (node->_children[i] == NULL)
            compact++;
    }
    if (compact > 0) {
        node->_nchild = 0;
        for (i = 0; i < node->_arity; i++) {
            if (node->_children[i]) {
                if (i > node->_nchild) {
                    // shift remaining nodes down - remember to update _childno for each node..
                    node->_children[node->_nchild] = node->_children[i];
                    node->_children[i]->_childno = node->_nchild;
                }
                node->_nchild++;
            }
        }
        assert(node->_arity == node->_nchild + compact);
        node->_arity = node->_nchild;
    }

    if (node->_arity <= 1) {
        QueryExpr* ret = NULL;
        if (node->_arity == 1) {
            ret = node->_children[0];
            node->_children[0] = NULL;
            ret->_parent = node->_parent;
            ret->_childno = node->_childno;
        }
        delete node;
        orig_stack = ret;
    }
}


// default implementation of 2nd visit to QueryNode objs:
void IQueryExprVisitor::RevisitQueryNode(QueryNode*)
{ }


// visitor pattern:

void QueryTerm::Accept(IQueryExprVisitor& v)
{
    v.VisitQueryTerm(this);
}


void QueryNode::Accept(IQueryExprVisitor& v)
{
    int i;
    v.VisitQueryNode(this);
    for (i = 0; i < _arity; i++)
        _children[i]->Accept(v);
    v.RevisitQueryNode(this);
}
