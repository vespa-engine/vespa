// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "queryterm.h"
#include "base.h"
#include <vespa/searchlib/parsequery/stackdumpiterator.h>

namespace search
{

/**
   Base class for all N-ary query operators.
   Implements the width, depth, print, and collect all leafs operators(terms).
*/
class QueryConnector : public QueryNode, public QueryNodeList
{
public:
    QueryConnector(const char * opName);
    ~QueryConnector();
    const HitList & evaluateHits(HitList & hl) const override;
    void reset() override;
    void getLeafs(QueryTermList & tl) override;
    void getLeafs(ConstQueryTermList & tl) const override;
    void getPhrases(QueryNodeRefList & tl) override;
    void getPhrases(ConstQueryNodeRefList & tl) const override;
    size_t depth() const override;
    size_t width() const override;
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;
    void setIndex(const vespalib::string & index) override { _index = index; }
    const vespalib::string & getIndex() const override { return _index; }
    static QueryConnector * create(ParseItem::ItemType type);
    virtual bool isFlattenable(ParseItem::ItemType type) const { (void) type; return false; }
private:
    vespalib::string _opName;
    vespalib::string _index;
};

/**
   True operator. Matches everything.
*/
class TrueNode : public QueryConnector
{
public:
    TrueNode() : QueryConnector("AND") { }
    bool evaluate() const override;
};

/**
   N-ary Or operator that simply ANDs all the nodes together.
*/
class AndQueryNode : public QueryConnector
{
public:
    AndQueryNode() : QueryConnector("AND") { }
    AndQueryNode(const char * opName) : QueryConnector(opName) { }
    bool evaluate() const override;
    bool isFlattenable(ParseItem::ItemType type) const override { return type == ParseItem::ITEM_AND; }
};

/**
   N-ary special AndNot operator. n[0] & !n[1] & !n[2] .. & !n[j].
*/
class AndNotQueryNode : public QueryConnector
{
public:
    AndNotQueryNode() : QueryConnector("ANDNOT") { }
    bool evaluate() const override;
    bool isFlattenable(ParseItem::ItemType type) const override { return type == ParseItem::ITEM_NOT; }
};

/**
   N-ary Or operator that simply ORs all the nodes together.
*/
class OrQueryNode : public QueryConnector
{
public:
    OrQueryNode() : QueryConnector("OR") { }
    OrQueryNode(const char * opName) : QueryConnector(opName) { }
    bool evaluate() const override;
    bool isFlattenable(ParseItem::ItemType type) const override {
        return (type == ParseItem::ITEM_OR) ||
               (type == ParseItem::ITEM_DOT_PRODUCT) ||
               (type == ParseItem::ITEM_WAND) ||
               (type == ParseItem::ITEM_WEAK_AND);
    }
};

/**
   N-ary "EQUIV" operator that merges terms from nodes below.
*/
class EquivQueryNode : public OrQueryNode
{
public:
    EquivQueryNode() : OrQueryNode("EQUIV") { }
    bool evaluate() const override;
    bool isFlattenable(ParseItem::ItemType type) const override {
        return (type == ParseItem::ITEM_EQUIV) ||
               (type == ParseItem::ITEM_WEIGHTED_SET);
    }
};

/**
   N-ary phrase operator. All terms must be satisfied and have the correct order
   with distance to next term equal to 1.
*/
class PhraseQueryNode : public AndQueryNode
{
public:
    PhraseQueryNode() : AndQueryNode("PHRASE"), _fieldInfo(32) { }
    bool evaluate() const override;
    const HitList & evaluateHits(HitList & hl) const override;
    void getPhrases(QueryNodeRefList & tl) override;
    void getPhrases(ConstQueryNodeRefList & tl) const override;
    const QueryTerm::FieldInfo & getFieldInfo(size_t fid) const { return _fieldInfo[fid]; }
    size_t getFieldInfoSize() const { return _fieldInfo.size(); }
    bool isFlattenable(ParseItem::ItemType type) const override { return type == ParseItem::ITEM_NOT; }
private:
    mutable std::vector<QueryTerm::FieldInfo> _fieldInfo;
    void updateFieldInfo(size_t fid, size_t offset, size_t fieldLength) const;
#if WE_EVER_NEED_TO_CACHE_THIS_WE_MIGHT_WANT_SOME_CODE_HERE
    HitList _cachedHitList;
    bool    _evaluated;
#endif
};

/**
   Unary Not operator. Just inverts the nodes result.
*/
class NotQueryNode : public QueryConnector
{
public:
    NotQueryNode() : QueryConnector("NOT") { }
    bool evaluate() const override;
};

/**
   N-ary Near operator. All terms must be within the given distance.
*/
class NearQueryNode : public AndQueryNode
{
public:
    NearQueryNode() : AndQueryNode("NEAR"), _distance(0) { }
    NearQueryNode(const char * opName) : AndQueryNode(opName), _distance(0) { }
    bool evaluate() const override;
    void distance(size_t dist)       { _distance = dist; }
    size_t distance()          const { return _distance; }
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    bool isFlattenable(ParseItem::ItemType type) const override { return type == ParseItem::ITEM_NOT; }
private:
    size_t _distance;
};

/**
   N-ary Ordered near operator. The terms must be in order and the distance between
   the first and last must not exceed the given distance.
*/
class ONearQueryNode : public NearQueryNode
{
public:
    ONearQueryNode() : NearQueryNode("ONEAR") { }
    ~ONearQueryNode() { }
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
    Query(const QueryNodeResultFactory & factory, const QueryPacketT & queryRep);
    /// Will build the query tree
    bool build(const QueryNodeResultFactory & factory, const QueryPacketT & queryRep);
    /// Will clear the results from the querytree.
    void reset();
    /// Will get all leafnodes.
    void getLeafs(QueryTermList & tl);
    void getLeafs(ConstQueryTermList & tl) const;
    /// Gives you all phrases of this tree.
    void getPhrases(QueryNodeRefList & tl);
    void getPhrases(ConstQueryNodeRefList & tl) const;
    bool evaluate() const;
    size_t depth() const;
    size_t width() const;
    bool valid() const { return _root.get() != NULL; }
    const QueryNode & getRoot() const { return *_root; }
    static QueryNode::UP steal(Query && query) { return std::move(query._root); }
private:
    QueryNode::UP _root;
};

}
