// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "queryterm.h"
#include <vespa/searchlib/parsequery/parse.h>
#include <optional>

namespace search { class SerializedQueryTree; }

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
    const HitList & evaluateHits(HitList & hl) override;
    void unpack_match_data(uint32_t docid, fef::MatchData& match_data, const fef::IIndexEnvironment& index_env,
                           search::common::ElementIds element_ids) override;
    void reset() override;
    void getLeaves(QueryTermList & tl) override;
    void getLeaves(ConstQueryTermList & tl) const override;
    size_t depth() const override;
    size_t width() const override;
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;
    void setIndex(std::string index) override { _index = std::move(index); }
    const std::string & getIndex() const override { return _index; }
    static std::unique_ptr<QueryConnector> create(ParseItem::ItemType type, const QueryNodeResultFactory& factory);
    virtual bool isFlattenable(ParseItem::ItemType type) const { (void) type; return false; }
    const QueryNodeList & getChildren() const { return _children; }
    virtual void addChild(std::unique_ptr<QueryNode> child);
    size_t size() const { return _children.size(); }
    const std::unique_ptr<QueryNode> & operator [](size_t index) const { return _children[index]; }
private:
    std::string _opName;
    std::string _index;
    QueryNodeList _children;
protected:
    std::optional<bool> _cached_evaluate_result;
};

/**
   True operator. Matches everything.
*/
class TrueNode : public QueryConnector
{
public:
    TrueNode() noexcept : QueryConnector("AND") { }
    ~TrueNode() override;
    bool evaluate() override;
    void get_element_ids(std::vector<uint32_t>& element_ids) override;
};

/** False operator. Matches nothing. */
class FalseNode : public QueryConnector
{
public:
    FalseNode() noexcept : QueryConnector("AND") { }
    ~FalseNode() override;
    bool evaluate() override;
    void get_element_ids(std::vector<uint32_t>& element_ids) override;
};

/**
   N-ary Or operator that simply ANDs all the nodes together.
*/
class AndQueryNode : public QueryConnector
{
public:
    AndQueryNode() noexcept : QueryConnector("AND") { }
    explicit AndQueryNode(const char * opName) noexcept : QueryConnector(opName) { }
    ~AndQueryNode() override;
    bool evaluate() override;
    bool isFlattenable(ParseItem::ItemType type) const override { return type == ParseItem::ITEM_AND; }
    void get_element_ids(std::vector<uint32_t>& element_ids) override;
};

/**
   N-ary special AndNot operator. n[0] & !n[1] & !n[2] .. & !n[j].
*/
class AndNotQueryNode : public QueryConnector
{
    bool _elementwise; // Node is descendant of SameElementQueryNode
public:
    AndNotQueryNode(bool elementwise) noexcept;
    ~AndNotQueryNode() override;
    bool evaluate() override;
    bool isFlattenable(ParseItem::ItemType) const override { return false; }
    void get_element_ids(std::vector<uint32_t>& element_ids) override;
};

/**
   N-ary Or operator that simply ORs all the nodes together.
*/
class OrQueryNode : public QueryConnector
{
public:
    OrQueryNode() noexcept : QueryConnector("OR") { }
    explicit OrQueryNode(const char * opName) noexcept : QueryConnector(opName) { }
    ~OrQueryNode() override;
    bool evaluate() override;
    bool isFlattenable(ParseItem::ItemType type) const override {
        return (type == ParseItem::ITEM_OR) ||
               (type == ParseItem::ITEM_WEAK_AND);
    }
    void get_element_ids(std::vector<uint32_t>& element_ids) override;
};

/**
   N-ary RankWith operator
*/
class RankWithQueryNode : public QueryConnector
{
public:
    RankWithQueryNode() noexcept : QueryConnector("RANK") { }
    explicit RankWithQueryNode(const char * opName) noexcept : QueryConnector(opName) { }
    ~RankWithQueryNode() override;
    bool evaluate() override;
    void get_element_ids(std::vector<uint32_t>& element_ids) override;
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
    Query(const QueryNodeResultFactory & factory, const SerializedQueryTree& queryTree);
    Query(const Query&) = delete;
    Query(Query&&) noexcept;
    ~Query();
    Query& operator=(const Query&) = delete;
    Query& operator=(Query&&) noexcept;
    /// Will build the query tree
    bool build(const QueryNodeResultFactory & factory, const SerializedQueryTree& queryTree);
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
    static std::unique_ptr<QueryNode> steal(Query && query) { return std::move(query._root); }
private:
    std::unique_ptr<QueryNode> _root;
};

}
