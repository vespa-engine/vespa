// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/* $Id$ */

#pragma once

#include <string>
#include <vector>
#include <vespa/fastlib/text/unicodeutil.h>
#include "querymodifier.h"

/** The internal query data structure used by the matching engine
 *  in Matcher.h
 */


// Option bit definitions:
#define X_ORDERED   0x1  // PHRASE and WITHIN operator have the ordered property
#define X_LIMIT     0x2  // NEAR and WITHIN operators have the limit property
#define X_EXACT     0x4  // PHRASE and descendants have this property
#define X_COMPLETE  0x8  // All keywords must be present (NEAR/PHRASE/WITHIN)
#define X_AND       0x10  // threshold must be recomputed when complete - AND semantics
#define X_OR        0x20  // threshold must be recomputed when complete + OR semantics
#define X_ANY       0x40  // threshold must be recomputed when complete + ANY semantics
#define X_CONSTR   0x100  // Bit telling if this subquery has constraints applied somewhere
#define X_CHKVAL   0x200  // Bit set if validity of keyword occurrences must be checked
#define X_NOT      0x400  // Limit has opposite sign (eliminate below: NOT_WITHIN semantics)
#define X_PREFIX  0x1000  // This is a prefix search (valid on terms only)
#define X_POSTFIX 0x2000  // This is a postfix search (valid on terms only)
#define X_WILD    0x4000  // This is a wildcard search (valid on terms only)
#define X_ONLY_1  0x8000  // Tell simplifier to delete all childs but #1 (RANK/ANDNOT)
#define X_SPECIALTOKEN  0x10000  // This is a special token (valid on terms only)

class QueryNode;
class QueryTerm;

typedef std::vector<QueryNode*> querynode_vector;


// Support slightly extended visitor pattern for QueryExpr nodes..

class IQueryExprVisitor
{
public:
    virtual ~IQueryExprVisitor() {}

    // Visit before visiting subnodes
    virtual void VisitQueryNode(QueryNode*) = 0;
    // visit after visiting subnodes - default: do nothing
    virtual void RevisitQueryNode(QueryNode*);

    virtual void VisitQueryTerm(QueryTerm*) = 0;
};


/** Base class for query expressions in Juniper */
class QueryExpr
{
public:
    explicit QueryExpr(int weight, int arity);
    explicit QueryExpr(QueryExpr* e);

    /** Add a child to the end of the list of children for this node.
     * @param child A pointer to a child node to add or NULL to denote that
     *    we have eliminated a child from this node - to trigger an arity update
     * @return A pointer to this node if more children are needed or else nearest
     *   parent that needs more children
     */
    virtual QueryNode* AddChild(QueryExpr* child) = 0;
    virtual ~QueryExpr();
    virtual int Limit() = 0;
    virtual void Dump(std::string&) = 0;
    virtual bool StackComplete() = 0;
    virtual void ComputeThreshold();
    virtual QueryNode* AsNode() = 0;
    virtual QueryTerm* AsTerm() = 0;
    virtual bool Complex() = 0;

    virtual void Accept(IQueryExprVisitor& v) = 0;

    virtual int MaxArity() { return 0; }

    inline bool HasConstraints() { return _options & X_CONSTR; }
    inline bool UsesValid() { return _options & X_CHKVAL; }
    inline bool HasLimit() { return _options & X_LIMIT; }
    inline bool Exact() { return _options & X_EXACT; }

    int _options;       // Applied options (bitmap) for this node
    int _weight;        // Weight of this term by parent - if 0: weight is sum of children
    int _arity;         // Arity of this query subexpression (may get decremented..)
    QueryNode* _parent; // Pointer to parent or NULL if this is the root of the query
    int _childno;       // Position number within parent's children (0 if no parents)

private:
    QueryExpr(QueryExpr &);
    QueryExpr &operator=(QueryExpr &);
};


/** Internal node of a query
 */
class QueryNode : public QueryExpr
{
public:
    // Create a new node with arity children
    QueryNode(int arity, int threshold, int weight = 0);

    // Create a copy of the node n wrt. arity etc, but without adding any children..
    explicit QueryNode(QueryNode* n);

    ~QueryNode();
    QueryNode* AddChild(QueryExpr* child) override;
    int Limit() override;
    bool Complete() const { return _arity == _nchild; }
    void Dump(std::string&) override;
    bool StackComplete() override;
    void ComputeThreshold() override;
    QueryNode* AsNode() override;
    QueryTerm* AsTerm() override;
    bool Complex() override;
    int MaxArity() override;

    void Accept(IQueryExprVisitor& v) override;

    // return true if a match for n should lead to creation of a new candidate node
    // corresponding to this query tree node:
    bool AcceptsInitially(QueryExpr* n);

    int _threshold;       // Threshold for this expression node to be considered complete
    int _limit;           // NEAR/WITHIN limit if X_LIMIT option set

    /* Pointer to an array of length _arity of pointers to
     * subqueries associated with this query */
    QueryExpr** _children;
    int _nchild;   // end pointer (fill level) of _children
    int _node_idx; // Index (position) of this nonterminal within table of all nonterminals

private:
    QueryNode(QueryNode &);
    QueryNode &operator=(QueryNode &);
};


/** Terminal node of a query
 */
class QueryTerm : public QueryExpr
{
public:
    QueryTerm(const char* t, int length, int ix, int weight = 100);
    explicit QueryTerm(QueryTerm* const);
    ~QueryTerm();
    int Limit() override;
    QueryNode* AddChild(QueryExpr* child) override;
    void Dump(std::string&) override;
    bool StackComplete() override;
    QueryNode* AsNode() override;
    QueryTerm* AsTerm() override;
    bool Complex() override;

    void Accept(IQueryExprVisitor& v) override;
    inline const char* term() { return _rep; }
    inline const ucs4_t* ucs4_term() { return _ucs4_term; }
    inline bool is_prefix() { return _options & X_PREFIX; }
    inline bool is_wildcard() { return _options & X_WILD; }
    inline bool isSpecialToken() { return _options & X_SPECIALTOKEN; }

    size_t len;
    size_t ucs4_len;
    int total_match_cnt;
    int exact_match_cnt;
    int idx;
    juniper::Rewriter* rewriter;
    juniper::string_matcher* reduce_matcher;
private:
    char* _rep;
    ucs4_t* _ucs4_term;

    QueryTerm(QueryTerm &);
    QueryTerm &operator=(QueryTerm &);
};


/** Modify the given stack by eliminating unnecessary internal nodes
 *  with arity 1 or non-terms with arity 0
 */
void SimplifyStack(QueryExpr*& orig_stack);
