// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "queryterm.h"
#include <vespa/searchlib/parsequery/parse.h>

namespace search::streaming {

/**
   Base class for all N-ary query operators.
   Implements the width, depth, print, and collect all leafs operators(terms).
*/
class QueryConnector : public QueryNode
{
public:
    explicit QueryConnector(const char * opName) noexcept;
    ~QueryConnector() override;
    const HitList & evaluateHits(HitList & hl) const override;
    void reset() override;
    void getLeaves(QueryTermList & tl) override;
    void getLeaves(ConstQueryTermList & tl) const override;
    size_t depth() const override;
    size_t width() const override;
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;
    void setIndex(const vespalib::string & index) override { _index = index; }
    const vespalib::string & getIndex() const override { return _index; }
    static std::unique_ptr<QueryConnector> create(ParseItem::ItemType type);
    virtual bool isFlattenable(ParseItem::ItemType type) const { (void) type; return false; }
    const QueryNodeList & getChildren() const { return _children; }
    virtual void addChild(QueryNode::UP child);
    size_t size() const { return _children.size(); }
    const QueryNode::UP & operator [](size_t index) const { return _children[index]; }
private:
    vespalib::string _opName;
    vespalib::string _index;
    QueryNodeList _children;
};

/**
   True operator. Matches everything.
*/
class TrueNode : public QueryConnector
{
public:
    TrueNode() noexcept : QueryConnector("AND") { }
    bool evaluate() const override;
};

/** False operator. Matches nothing. */
class FalseNode : public QueryConnector
{
public:
    FalseNode() noexcept : QueryConnector("AND") { }
    bool evaluate() const override;
};

/**
   N-ary Or operator that simply ANDs all the nodes together.
*/
class AndQueryNode : public QueryConnector
{
public:
    AndQueryNode() noexcept : QueryConnector("AND") { }
    explicit AndQueryNode(const char * opName) noexcept : QueryConnector(opName) { }
    bool evaluate() const override;
    bool isFlattenable(ParseItem::ItemType type) const override { return type == ParseItem::ITEM_AND; }
};

/**
   N-ary special AndNot operator. n[0] & !n[1] & !n[2] .. & !n[j].
*/
class AndNotQueryNode : public QueryConnector
{
public:
    AndNotQueryNode() noexcept : QueryConnector("ANDNOT") { }
    bool evaluate() const override;
    bool isFlattenable(ParseItem::ItemType) const override { return false; }
};

/**
   N-ary Or operator that simply ORs all the nodes together.
*/
class OrQueryNode : public QueryConnector
{
public:
    OrQueryNode() noexcept : QueryConnector("OR") { }
    explicit OrQueryNode(const char * opName) noexcept : QueryConnector(opName) { }
    bool evaluate() const override;
    bool isFlattenable(ParseItem::ItemType type) const override {
        return (type == ParseItem::ITEM_OR) ||
               (type == ParseItem::ITEM_WEAK_AND);
    }
};

/**
   N-ary RankWith operator
*/
class RankWithQueryNode : public QueryConnector
{
public:
    RankWithQueryNode() noexcept : QueryConnector("RANK") { }
    explicit RankWithQueryNode(const char * opName) noexcept : QueryConnector(opName) { }
    bool evaluate() const override;
};

/**
   Query packages the query tree. The usage pattern is like this.
   Construct the tree with the correct tree description.
   Get the leaf nodes and populate them with the term occurences.
   Then evaluate the query. This is repeated for each document or chunk that
   you want to process. The tree can also be printed. And you can read the
   width and depth properties.
*/
class Query
{
public:
    Query();
    Query(const QueryNodeResultFactory & factory, std::string_view queryRep);
    /// Will build the query tree
    bool build(const QueryNodeResultFactory & factory, std::string_view queryRep);
    /// Will clear the results from the querytree.
    void reset();
    /// Will get all leafnodes.
    void getLeaves(QueryTermList & tl);
    void getLeaves(ConstQueryTermList & tl) const;
    bool evaluate() const;
    size_t depth() const;
    size_t width() const;
    bool valid() const { return _root.get() != nullptr; }
    const QueryNode & getRoot() const { return *_root; }
    QueryNode & getRoot() { return *_root; }
    static QueryNode::UP steal(Query && query) { return std::move(query._root); }
private:
    QueryNode::UP _root;
};

}
