// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "query_connector.h"
#include "true_node.h"
#include "false_node.h"
#include "and_query_node.h"
#include "and_not_query_node.h"
#include "or_query_node.h"
#include "rank_with_query_node.h"

namespace search { class SerializedQueryTree; }

namespace search::streaming {

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
